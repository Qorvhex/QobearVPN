package com.qorvhex.qobearvpn.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import com.qorvhex.qobearvpn.data.ConnectionLogStore
import com.qorvhex.qobearvpn.model.BypassRegion
import com.qorvhex.qobearvpn.model.ProbeResult
import com.qorvhex.qobearvpn.model.Profile
import com.qorvhex.qobearvpn.model.RelayProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import kotlin.concurrent.thread
import kotlin.coroutines.resume

object OpenVpnLatency {
    private const val MAX_CANDIDATES = 6
    private const val REPLY_MS = 3_500
    private const val TLS_MS = 5_000
    private const val FLIGHT_BYTES = 2048

    private data class Handshake(val rttMs: Long?, val tls: Boolean)

    private val tlsContext by lazy {
        SSLContext.getInstance("TLS").apply { init(null, null, null) }
    }

    suspend fun measure(
        context: Context,
        profile: Profile,
        bypassRegion: BypassRegion,
    ): ProbeResult = withContext(Dispatchers.IO) {
        val relay = bypassRegion.relayProfile
        val physicalNetwork = activePhysicalNetwork(context)

        if (ProfileRuntime.isTunnelBear(profile)) {
            return@withContext measureTunnelBear(profile, relay, physicalNetwork)
        }
        val remote = profile.remotes.first()
        val tcp = remote.protocol.startsWith("tcp")
        val method = if (tcp) "OPENVPN_TCP_TLS" else "OPENVPN_UDP_TLS"
        if (profile.authenticatedControl) {
            return@withContext measureConnectOnly(remote.host, remote.port, remote.protocol, physicalNetwork)
        }
        try {
            currentCoroutineContext().ensureActive()
            val resolved = physicalNetwork?.getAllByName(remote.host) ?: InetAddress.getAllByName(remote.host)
            val address = resolved.firstOrNull {
                when {
                    remote.protocol.contains('4') -> it is Inet4Address
                    remote.protocol.contains('6') -> it is Inet6Address
                    else -> true
                }
            } ?: error("No address resolved")

            val r = if (tcp) {
                handshakeTcp(address, remote.port, relay, physicalNetwork)
            } else {
                handshakeUdp(address, remote.port, physicalNetwork)
            }
            when {
                r.rttMs == null -> ProbeResult(null, System.currentTimeMillis(), method, "NO_RESPONSE")
                !r.tls -> ProbeResult(null, System.currentTimeMillis(), method, "TLS_BLOCKED")
                else -> ProbeResult(r.rttMs, System.currentTimeMillis(), method, healthy = listOfNotNull(address.hostAddress))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ConnectionLogStore.append("Probe", "${profile.name}: ${e.message}")
            ProbeResult(null, System.currentTimeMillis(), method, "NO_RESPONSE")
        }
    }

    private suspend fun measureConnectOnly(
        host: String,
        port: Int,
        protocol: String,
        physicalNetwork: Network?,
    ): ProbeResult {
        if (!protocol.startsWith("tcp")) {
            return ProbeResult(null, System.currentTimeMillis(), "OPENVPN_UDP_AUTH", "CONTROL_AUTH")
        }
        return try {
            val resolved = physicalNetwork?.getAllByName(host) ?: InetAddress.getAllByName(host)
            val address = resolved.firstOrNull {
                when {
                    protocol.contains('4') -> it is Inet4Address
                    protocol.contains('6') -> it is Inet6Address
                    else -> true
                }
            } ?: return ProbeResult(null, System.currentTimeMillis(), "TCP_CONNECT", "NO_RESPONSE")

            val rtt = withCancellableSocket(physicalNetwork) { socket ->
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(address, port), REPLY_MS)
                ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
            }
            ProbeResult(rtt, System.currentTimeMillis(), "TCP_CONNECT", healthy = listOfNotNull(address.hostAddress))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ProbeResult(null, System.currentTimeMillis(), "TCP_CONNECT", "NO_RESPONSE")
        }
    }

    private suspend fun measureTunnelBear(
        profile: Profile,
        relay: RelayProfile?,
        physicalNetwork: Network?,
    ): ProbeResult = coroutineScope {
        val method = if (relay != null) "OPENVPN_DPI_TLS" else "OPENVPN_DIRECT_TLS"
        val candidates = candidates(profile, physicalNetwork)
        if (candidates.isEmpty()) {
            return@coroutineScope ProbeResult(null, System.currentTimeMillis(), method, "NO_RESPONSE")
        }
        val results = candidates.map { ip ->
            async {
                val res = try {
                    handshakeTcp(InetAddress.getByName(ip), ProfileRuntime.TUNNELBEAR_TCP_PORT, relay, physicalNetwork)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Handshake(null, false)
                }
                ip to res
            }
        }.awaitAll()

        val passed = results.filter { it.second.tls && it.second.rttMs != null }
            .sortedBy { it.second.rttMs }

        when {
            passed.isNotEmpty() -> ProbeResult(
                millis = passed.first().second.rttMs,
                checkedAt = System.currentTimeMillis(),
                method = method,
                healthy = passed.map { it.first },
            )
            results.any { it.second.rttMs != null } ->
                ProbeResult(null, System.currentTimeMillis(), method, "TLS_BLOCKED")
            else ->
                ProbeResult(null, System.currentTimeMillis(), method, "NO_RESPONSE")
        }
    }

    suspend fun candidates(profile: Profile, physicalNetwork: Network? = null): List<String> {
        val host = profile.remotes.first().host
        val fresh = runCatching {
            val all = physicalNetwork?.getAllByName(host) ?: InetAddress.getAllByName(host)
            all.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }
        }.getOrDefault(emptyList())
        currentCoroutineContext().ensureActive()
        return (fresh + profile.probe?.healthy.orEmpty()).distinct().take(MAX_CANDIDATES)
    }

    private suspend fun handshakeTcp(
        address: InetAddress,
        port: Int,
        relay: RelayProfile?,
        physicalNetwork: Network?,
    ): Handshake = suspendCancellableCoroutine { cont ->
        val socket = runCatching {
            physicalNetwork?.socketFactory?.createSocket() ?: Socket()
        }.getOrElse {
            cont.resume(Handshake(null, false))
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { runCatching { socket.close() } }

        thread(name = "qobear-probe-tcp", isDaemon = true) {
            try {
                socket.use { s ->
                    s.tcpNoDelay = true
                    val start = System.nanoTime()
                    s.connect(InetSocketAddress(address, port), REPLY_MS)
                    if (relay != null) clampWindow(s, relay.clampWindow)
                    s.soTimeout = REPLY_MS
                    val out = s.getOutputStream()
                    val input = DataInputStream(s.getInputStream())
                    val session = ByteArray(8).also { SecureRandom().nextBytes(it) }
                    out.write(frame(OpenVpnProbePacket.request(session)))
                    out.flush()

                    val reply = readFrame(input)
                    if (!OpenVpnProbePacket.accepts(reply, session)) {
                        if (cont.isActive) cont.resume(Handshake(null, false))
                        return@thread
                    }
                    val rtt = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)

                    val hello = OpenVpnProbePacket.control(
                        session = session,
                        ackId = OpenVpnProbePacket.packetId(reply),
                        remoteSession = OpenVpnProbePacket.serverSession(reply),
                        packetId = 1L,
                        payload = clientHello(),
                    )
                    val framed = frame(hello)
                    if (relay != null) {
                        writeSplitBlocking(out, framed, relay) { !cont.isActive }
                    } else {
                        out.write(framed)
                        out.flush()
                    }

                    val deadline = System.currentTimeMillis() + TLS_MS
                    var got = 0
                    while (cont.isActive && System.currentTimeMillis() < deadline) {
                        s.soTimeout = (deadline - System.currentTimeMillis()).toInt().coerceIn(1, TLS_MS)
                        val pkt = try {
                            readFrame(input)
                        } catch (_: Exception) {
                            break
                        }
                        val tls = OpenVpnProbePacket.controlPayload(pkt) ?: continue
                        if (tls.isNotEmpty() && tls[0].toInt() == 0x15) {
                            if (cont.isActive) cont.resume(Handshake(rtt, true))
                            return@thread
                        }
                        got += tls.size
                        if (got >= FLIGHT_BYTES) {
                            if (cont.isActive) cont.resume(Handshake(rtt, true))
                            return@thread
                        }
                    }
                    if (cont.isActive) cont.resume(Handshake(rtt, false))
                }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(Handshake(null, false))
            }
        }
    }

    private suspend fun handshakeUdp(
        address: InetAddress,
        port: Int,
        physicalNetwork: Network?,
    ): Handshake = suspendCancellableCoroutine { cont ->
        val socket = DatagramSocket()
        runCatching { physicalNetwork?.bindSocket(socket) }
        cont.invokeOnCancellation { runCatching { socket.close() } }

        thread(name = "qobear-probe-udp", isDaemon = true) {
            try {
                socket.use { s ->
                    s.connect(address, port)
                    val session = ByteArray(8).also { SecureRandom().nextBytes(it) }
                    val start = System.nanoTime()
                    val request = OpenVpnProbePacket.request(session)
                    s.send(DatagramPacket(request, request.size))

                    val deadline = System.nanoTime() + REPLY_MS * 1_000_000L
                    var reply: ByteArray? = null
                    while (cont.isActive && reply == null && System.nanoTime() < deadline) {
                        s.soTimeout = ((deadline - System.nanoTime()) / 1_000_000).toInt().coerceIn(1, REPLY_MS)
                        val packet = DatagramPacket(ByteArray(2048), 2048)
                        try {
                            s.receive(packet)
                        } catch (_: Exception) {
                            break
                        }
                        val data = packet.data.copyOf(packet.length)
                        if (OpenVpnProbePacket.accepts(data, session)) reply = data
                    }
                    val r = reply ?: run {
                        if (cont.isActive) cont.resume(Handshake(null, false))
                        return@thread
                    }
                    val rtt = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)

                    val hello = OpenVpnProbePacket.control(
                        session = session,
                        ackId = OpenVpnProbePacket.packetId(r),
                        remoteSession = OpenVpnProbePacket.serverSession(r),
                        packetId = 1L,
                        payload = clientHello(),
                    )
                    s.send(DatagramPacket(hello, hello.size))

                    val until = System.currentTimeMillis() + TLS_MS
                    var got = 0
                    while (cont.isActive && System.currentTimeMillis() < until) {
                        s.soTimeout = (until - System.currentTimeMillis()).toInt().coerceIn(1, TLS_MS)
                        val packet = DatagramPacket(ByteArray(4096), 4096)
                        try {
                            s.receive(packet)
                        } catch (_: Exception) {
                            break
                        }
                        val tls = OpenVpnProbePacket.controlPayload(packet.data.copyOf(packet.length)) ?: continue
                        if (tls.isNotEmpty() && tls[0].toInt() == 0x15) {
                            if (cont.isActive) cont.resume(Handshake(rtt, true))
                            return@thread
                        }
                        got += tls.size
                        if (got >= FLIGHT_BYTES) {
                            if (cont.isActive) cont.resume(Handshake(rtt, true))
                            return@thread
                        }
                    }
                    if (cont.isActive) cont.resume(Handshake(rtt, false))
                }
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(Handshake(null, false))
            }
        }
    }

    private suspend fun <T> withCancellableSocket(
        physicalNetwork: Network?,
        block: (Socket) -> T,
    ): T = suspendCancellableCoroutine { cont ->
        val socket = physicalNetwork?.socketFactory?.createSocket() ?: Socket()
        cont.invokeOnCancellation { runCatching { socket.close() } }
        thread(name = "qobear-probe-conn", isDaemon = true) {
            try {
                val res = socket.use(block)
                if (cont.isActive) cont.resume(res)
            } catch (e: Exception) {
                if (cont.isActive) cont.cancel(e)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun activePhysicalNetwork(context: Context): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        return cm.allNetworks.firstOrNull { net ->
            val caps = cm.getNetworkCapabilities(net)
            caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
    }

    private fun frame(packet: ByteArray): ByteArray =
        byteArrayOf((packet.size ushr 8).toByte(), packet.size.toByte()) + packet

    private fun readFrame(input: DataInputStream): ByteArray {
        val length = input.readUnsignedShort()
        check(length in 1..4096)
        return ByteArray(length).also { input.readFully(it) }
    }

    private fun writeSplitBlocking(
        out: OutputStream,
        bytes: ByteArray,
        relay: RelayProfile,
        isCancelled: () -> Boolean,
    ) {
        val rng = SecureRandom()
        val spread = (relay.maxPiece - relay.minPiece + 1).coerceAtLeast(1)
        var at = 0
        var first = true
        while (at < bytes.size && !isCancelled()) {
            val size = if (first) 1 else relay.minPiece + rng.nextInt(spread)
            first = false
            val end = minOf(bytes.size, at + size)
            out.write(bytes, at, end - at)
            out.flush()
            at = end
            if (at < bytes.size && !isCancelled()) {
                val delayMs = relay.pauseMinMs + rng.nextInt(relay.pauseSpreadMs.coerceAtLeast(1))
                if (delayMs > 0) Thread.sleep(delayMs.toLong())
            }
        }
    }

    private fun clampWindow(socket: Socket, bytes: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            ParcelFileDescriptor.fromSocket(socket).use { pfd ->
                Os.setsockoptInt(pfd.fileDescriptor, 6, 10, bytes)
            }
        }
    }

    private fun clientHello(): ByteArray {
        val engine = tlsContext.createSSLEngine().apply { useClientMode = true }
        val out = ByteBuffer.allocate(engine.session.packetBufferSize)
        engine.beginHandshake()
        engine.wrap(ByteBuffer.allocate(0), out)
        out.flip()
        return ByteArray(out.remaining()).also { out.get(it) }
    }
}
