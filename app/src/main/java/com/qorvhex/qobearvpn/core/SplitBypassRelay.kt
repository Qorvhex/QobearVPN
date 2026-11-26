package com.qorvhex.qobearvpn.core

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.Os
import com.qorvhex.qobearvpn.data.ConnectionLogStore
import com.qorvhex.qobearvpn.model.RelayProfile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Local loopback TCP relay that disrupts DPI record-boundary matching on OpenVPN-over-TCP.
 */
class SplitBypassRelay(
    private val service: VpnService,
    private val hosts: List<String>,
    private val port: Int,
    private val profile: RelayProfile,
) {
    init {
        require(hosts.isNotEmpty())
    }

    private val indexLock = Any()
    private var nextIndex = 0
    private val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
    val localPort: Int get() = server.localPort

    @Volatile
    private var closed = false
    private val openSockets = mutableSetOf<Socket>()
    private val clientPermits = Semaphore(MAX_CONCURRENT_CLIENTS)
    private val loggedClampWarning = AtomicBoolean(false)

    fun start() {
        thread(name = "qobear-relay-accept", isDaemon = true) {
            while (!closed) {
                val client = try {
                    server.accept()
                } catch (e: Exception) {
                    if (!closed) {
                        ConnectionLogStore.append(TAG, "Relay accept halted: ${e.message}", isError = true)
                    }
                    break
                }
                if (!clientPermits.tryAcquire()) {
                    runCatching { client.close() }
                    continue
                }
                thread(name = "qobear-relay-conn", isDaemon = true) {
                    try {
                        serve(client)
                    } finally {
                        clientPermits.release()
                    }
                }
            }
        }
    }

    private fun registerSocket(socket: Socket): Boolean = synchronized(openSockets) {
        if (closed) {
            runCatching { socket.close() }
            false
        } else {
            openSockets += socket
            true
        }
    }

    private fun releaseSocket(socket: Socket?) {
        if (socket == null) return
        synchronized(openSockets) { openSockets.remove(socket) }
        runCatching { socket.close() }
    }

    private fun serve(client: Socket) {
        if (!registerSocket(client)) return
        var upstream: Socket? = null
        try {
            client.tcpNoDelay = true
            val (host, up) = connectUpstream()
            upstream = up
            val t0 = System.currentTimeMillis()
            ConnectionLogStore.append(TAG, "Relay connected -> $host:$port")

            val clamped = clampWindow(up, profile.clampWindow)
            val trace = Trace()
            if (clamped) {
                thread(name = "qobear-relay-unclamp", isDaemon = true) {
                    try {
                        while (!closed &&
                            trace.downBytes < profile.unclampBytes &&
                            System.currentTimeMillis() - t0 < profile.unclampMs
                        ) {
                            Thread.sleep(100)
                        }
                        if (!closed) clampWindow(up, OPEN_WINDOW)
                    } catch (_: Exception) {
                    }
                }
            }

            val downThread = thread(name = "qobear-relay-down", isDaemon = true) {
                try {
                    pump(up.getInputStream(), client.getOutputStream(), trace)
                    if (trace.downBytes < HANDSHAKE_BYTES) skipHost(host)
                } catch (e: Exception) {
                    if (!closed) ConnectionLogStore.append(TAG, "Relay down error: ${e.message}")
                    runCatching { client.close() }
                    runCatching { up.close() }
                } finally {
                    runCatching { client.shutdownOutput() }
                }
            }

            forwardSplit(client.getInputStream(), up.getOutputStream(), trace)
            if (trace.downBytes < HANDSHAKE_BYTES) skipHost(host)
            runCatching { up.shutdownOutput() }
            runCatching { downThread.join(1_000) }
        } catch (e: Exception) {
            if (!closed) {
                ConnectionLogStore.append(TAG, "Relay error: ${e.message ?: "connect failed"}", isError = true)
            }
        } finally {
            releaseSocket(upstream)
            releaseSocket(client)
        }
    }

    private fun connectUpstream(): Pair<String, Socket> {
        var lastError: Exception? = null
        val start = synchronized(indexLock) { nextIndex }
        for (i in hosts.indices) {
            if (closed) break
            val idx = (start + i) % hosts.size
            val socket = Socket()
            if (!registerSocket(socket)) throw IOException("Relay closed")
            try {
                socket.tcpNoDelay = true
                if (!service.protect(socket)) {
                    throw IOException("VpnService.protect failed for relay upstream")
                }
                val timeout = if (hosts.size > 1) FAILOVER_CONNECT_MS else CONNECT_MS
                socket.connect(InetSocketAddress(InetAddress.getByName(hosts[idx]), port), timeout)
                synchronized(indexLock) { nextIndex = idx }
                return hosts[idx] to socket
            } catch (e: IOException) {
                lastError = e
                releaseSocket(socket)
            }
        }
        throw lastError ?: IOException("Relay upstream unavailable")
    }

    private fun skipHost(host: String) {
        synchronized(indexLock) {
            if (hosts.size > 1 && hosts[nextIndex] == host) {
                nextIndex = (nextIndex + 1) % hosts.size
                ConnectionLogStore.append(TAG, "No handshake reply from $host, rotating to ${hosts[nextIndex]}")
            }
        }
    }

    private fun forwardSplit(input: InputStream, output: OutputStream, trace: Trace) {
        val rng = SecureRandom()
        val buf = ByteArray(16 * 1024)
        var budget = profile.splitBytes
        var first = true
        var resetRemaining = -1
        var firstLenByte: Int? = null

        while (true) {
            val n = input.read(buf)
            if (n < 0) return
            trace.upBytes += n
            var offset = 0

            if (resetRemaining != 0) {
                if (resetRemaining < 0) {
                    if (firstLenByte == null) {
                        if (n == 1) {
                            firstLenByte = buf[0].toInt() and 0xFF
                            output.write(buf, 0, 1)
                            output.flush()
                            continue
                        }
                        val len = ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
                        resetRemaining = 2 + len
                    } else {
                        val len = (firstLenByte shl 8) or (buf[0].toInt() and 0xFF)
                        firstLenByte = null
                        // Already sent 1st byte of header; remaining of reset is 1 + len
                        resetRemaining = 1 + len
                    }
                }
                val whole = minOf(resetRemaining, n)
                output.write(buf, 0, whole)
                output.flush()
                resetRemaining -= whole
                offset = whole
                if (offset >= n) continue
            }

            if (budget <= 0) {
                output.write(buf, offset, n - offset)
                output.flush()
                continue
            }

            val spread = (profile.maxPiece - profile.minPiece + 1).coerceAtLeast(1)
            while (offset < n) {
                val pieceSize = if (first) 1 else profile.minPiece + rng.nextInt(spread)
                first = false
                val end = minOf(n, offset + pieceSize)
                output.write(buf, offset, end - offset)
                output.flush()
                budget -= (end - offset)
                offset = end
                if (budget <= 0) {
                    if (offset < n) {
                        output.write(buf, offset, n - offset)
                        output.flush()
                    }
                    break
                }
                if (offset < n) {
                    val delay = profile.pauseMinMs + rng.nextInt(profile.pauseSpreadMs.coerceAtLeast(1))
                    if (delay > 0) Thread.sleep(delay.toLong())
                }
            }
        }
    }

    private fun clampWindow(socket: Socket, bytes: Int): Boolean = try {
        ParcelFileDescriptor.fromSocket(socket).use { pfd ->
            Os.setsockoptInt(pfd.fileDescriptor, IPPROTO_TCP, TCP_WINDOW_CLAMP, bytes)
        }
        true
    } catch (e: Exception) {
        if (loggedClampWarning.compareAndSet(false, true)) {
            ConnectionLogStore.append(TAG, "TCP window clamp unavailable: ${e.message}")
        }
        false
    }

    private fun pump(input: InputStream, output: OutputStream, trace: Trace) {
        val buf = ByteArray(32 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) return
            trace.downBytes += n
            output.write(buf, 0, n)
            output.flush()
        }
    }

    private class Trace {
        @Volatile var upBytes = 0L
        @Volatile var downBytes = 0L
    }

    fun close() {
        closed = true
        runCatching { server.close() }
        synchronized(openSockets) {
            openSockets.forEach { runCatching { it.close() } }
            openSockets.clear()
        }
    }

    companion object {
        private const val TAG = "SplitRelay"
        private const val MAX_CONCURRENT_CLIENTS = 4
        private const val CONNECT_MS = 8_000
        private const val FAILOVER_CONNECT_MS = 4_000
        private const val HANDSHAKE_BYTES = 2048L
        private const val IPPROTO_TCP = 6
        private const val TCP_WINDOW_CLAMP = 10
        private const val OPEN_WINDOW = 16 * 1024 * 1024
    }
}
