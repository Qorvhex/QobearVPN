package com.qorvhex.qobearvpn.data

import android.content.Context
import com.qorvhex.qobearvpn.core.ProfileImporter
import com.qorvhex.qobearvpn.model.Account
import com.qorvhex.qobearvpn.model.AuthState
import com.qorvhex.qobearvpn.model.ProbeResult
import com.qorvhex.qobearvpn.model.Profile
import com.qorvhex.qobearvpn.model.ProfileImportException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class QobearCatalog(
    val accounts: List<Account> = emptyList(),
    val profiles: List<Profile> = emptyList(),
    val activeAccountId: String? = null,
    val selectedProfileId: String? = null,
    val storageError: Boolean = false,
)

class QobearRepository private constructor(private val context: Context) {
    private val vault = QobearVault(context)
    private val mutable = MutableStateFlow(QobearCatalog())
    val data: StateFlow<QobearCatalog> = mutable.asStateFlow()

    init {
        loadCatalog()
    }

    @Synchronized
    private fun loadCatalog() {
        try {
            val bundled = loadBundledProfiles()
            val storedRaw = vault.read(VAULT_CATALOG)
            if (storedRaw != null) {
                val decoded = decode(JSONObject(storedRaw))
                val existingById = decoded.profiles.associateBy { it.id }
                val merged = bundled.map { b ->
                    existingById[b.id]?.let { old ->
                        b.copy(favorite = old.favorite, probe = old.probe)
                    } ?: b
                } + decoded.profiles.filter { it.isCustom }
                val next = decoded.copy(
                    profiles = merged,
                    selectedProfileId = decoded.selectedProfileId?.takeIf { id -> merged.any { it.id == id } }
                        ?: merged.firstOrNull()?.id,
                    storageError = false,
                )
                mutable.value = next
            } else {
                val initial = QobearCatalog(
                    profiles = bundled,
                    selectedProfileId = bundled.firstOrNull()?.id,
                )
                save(initial)
            }
        } catch (e: Exception) {
            ConnectionLogStore.append("Vault", "Encrypted catalog load failed: ${e.message}", isError = true)
            mutable.value = QobearCatalog(storageError = true)
        }
    }

    @Synchronized
    fun resetCorruptStorage() {
        runCatching { vault.remove(VAULT_CATALOG) }
        runCatching { vault.remove(VAULT_COMPANIONS) }
        val bundled = loadBundledProfiles()
        val initial = QobearCatalog(
            profiles = bundled,
            selectedProfileId = bundled.firstOrNull()?.id,
            storageError = false,
        )
        mutable.value = initial
        save(initial)
    }

    private fun loadBundledProfiles(): List<Profile> {
        val assetManager = context.assets
        val names = assetManager.list("profiles").orEmpty()
            .filter { it.endsWith(".ovpn", ignoreCase = true) }
            .sorted()
        return names.mapNotNull { fileName ->
            runCatching {
                val text = assetManager.open("profiles/$fileName").bufferedReader().use { it.readText() }
                ProfileImporter.parse(fileName, text, isCustom = false)
            }.onFailure { err ->
                ConnectionLogStore.append("Profiles", "Failed loading bundled $fileName: ${err.message}", isError = true)
            }.getOrNull()
        }
    }

    @Synchronized
    private fun save(next: QobearCatalog) {
        check(!mutable.value.storageError) { "Encrypted storage unavailable" }
        vault.write(VAULT_CATALOG, encode(next).toString())
        mutable.value = next
    }

    @Synchronized
    fun saveAccount(id: String?, username: String, password: String): Account {
        check(!mutable.value.storageError)
        val trimmedUser = username.trim()
        require(trimmedUser.isNotBlank() && trimmedUser.length <= 320)
        require(password.isNotEmpty() && password.length <= 4096)
        require('\u0000' !in trimmedUser && '\n' !in trimmedUser && '\r' !in trimmedUser)
        require('\u0000' !in password)

        val existing = id?.let { targetId -> mutable.value.accounts.firstOrNull { it.id == targetId } }
        val accountId = existing?.id ?: UUID.randomUUID().toString()
        val credentialRef = UUID.randomUUID().toString()
        vault.write("cred_$credentialRef", password)

        val account = Account(
            id = accountId,
            username = trimmedUser,
            auth = AuthState.UNKNOWN,
            lastConnected = existing?.lastConnected,
            lastError = null,
            credentialRef = credentialRef,
        )
        try {
            val updatedAccounts = mutable.value.accounts.filterNot { it.id == accountId } + account
            save(
                mutable.value.copy(
                    accounts = updatedAccounts,
                    activeAccountId = mutable.value.activeAccountId ?: accountId,
                )
            )
        } catch (e: Exception) {
            runCatching { vault.remove("cred_$credentialRef") }
            throw e
        }
        existing?.let { runCatching { vault.remove("cred_${it.credentialRef}") } }
        return account
    }

    @Synchronized
    fun selectAccount(id: String) {
        require(mutable.value.accounts.any { it.id == id })
        save(mutable.value.copy(activeAccountId = id))
    }

    @Synchronized
    fun removeAccount(id: String) {
        val target = mutable.value.accounts.firstOrNull { it.id == id } ?: return
        val remaining = mutable.value.accounts.filterNot { it.id == id }
        val nextActive = if (mutable.value.activeAccountId == id) remaining.firstOrNull()?.id else mutable.value.activeAccountId
        save(mutable.value.copy(accounts = remaining, activeAccountId = nextActive))
        runCatching { vault.remove("cred_${target.credentialRef}") }
    }

    @Synchronized
    fun updateAccount(id: String, transform: (Account) -> Account) {
        save(mutable.value.copy(accounts = mutable.value.accounts.map { if (it.id == id) transform(it) else it }))
    }

    @Synchronized
    fun password(accountId: String): String {
        val account = mutable.value.accounts.firstOrNull { it.id == accountId } ?: error("Account unavailable")
        return vault.read("cred_${account.credentialRef}") ?: error("Credential unavailable")
    }

    @Synchronized
    fun selectProfile(id: String) {
        require(mutable.value.profiles.any { it.id == id })
        save(mutable.value.copy(selectedProfileId = id))
    }

    @Synchronized
    fun updateProfile(id: String, transform: (Profile) -> Profile) {
        save(mutable.value.copy(profiles = mutable.value.profiles.map { if (it.id == id) transform(it) else it }))
    }

    @Synchronized
    fun updateProfileProbeInMemory(id: String, probe: ProbeResult) {
        if (mutable.value.storageError) return
        mutable.value = mutable.value.copy(
            profiles = mutable.value.profiles.map { if (it.id == id) it.copy(probe = probe) else it }
        )
    }

    @Synchronized
    fun persistCatalog() {
        if (mutable.value.storageError) return
        save(mutable.value)
    }

    @Synchronized
    fun removeCustomProfile(id: String) {
        val target = mutable.value.profiles.firstOrNull { it.id == id && it.isCustom } ?: return
        val remaining = mutable.value.profiles.filterNot { it.id == target.id }
        val nextSelected = if (mutable.value.selectedProfileId == id) remaining.firstOrNull()?.id else mutable.value.selectedProfileId
        save(mutable.value.copy(profiles = remaining, selectedProfileId = nextSelected))
        if (remaining.none { it.isCustom }) {
            runCatching { vault.remove(VAULT_COMPANIONS) }
        }
    }

    @Synchronized
    fun importFiles(files: Map<String, String>): ImportSummary {
        // Keep companions scoped strictly to this import batch plus any referenced inline assets are already embedded into Profile.config.
        val companions = files.filterKeys { !it.endsWith(".ovpn", true) && !it.endsWith(".ovpn.txt", true) }
        var imported = 0
        var duplicates = 0
        val errors = mutableListOf<String>()
        val profiles = mutable.value.profiles.toMutableList()

        files.filterKeys { it.endsWith(".ovpn", true) || it.endsWith(".ovpn.txt", true) }
            .forEach { (name, content) ->
                try {
                    val p = ProfileImporter.parse(name, content, companions, isCustom = true)
                    if (profiles.any { it.id == p.id }) {
                        duplicates++
                    } else {
                        profiles += p
                        imported++
                    }
                } catch (e: ProfileImportException) {
                    errors += "$name: ${e.message}"
                }
            }

        if (imported > 0) {
            save(
                mutable.value.copy(
                    profiles = profiles,
                    selectedProfileId = mutable.value.selectedProfileId ?: profiles.firstOrNull()?.id,
                )
            )
        }
        return ImportSummary(imported = imported, duplicates = duplicates, errors = errors)
    }

    data class ImportSummary(val imported: Int, val duplicates: Int, val errors: List<String>)

    private fun encode(c: QobearCatalog) = JSONObject().apply {
        put("version", 1)
        put("activeAccount", c.activeAccountId)
        put("selectedProfile", c.selectedProfileId)
        put("accounts", JSONArray(c.accounts.map { a ->
            JSONObject().apply {
                put("id", a.id)
                put("username", a.username)
                put("auth", a.auth.name)
                put("credentialRef", a.credentialRef)
                put("lastConnected", a.lastConnected)
                put("lastError", a.lastError)
            }
        }))
        put("profiles", JSONArray(c.profiles.map { p ->
            JSONObject().apply {
                put("name", p.name)
                put("config", p.config)
                put("favorite", p.favorite)
                put("isCustom", p.isCustom)
                p.probe?.let { pr ->
                    put("probe", JSONObject().apply {
                        put("ms", pr.millis)
                        put("checked", pr.checkedAt)
                        put("method", pr.method)
                        put("error", pr.error)
                        put("healthy", JSONArray(pr.healthy))
                    })
                }
            }
        }))
    }

    private fun decode(j: JSONObject): QobearCatalog {
        require(j.getInt("version") == 1)
        val accounts = j.getJSONArray("accounts").objects().map { a ->
            Account(
                id = a.getString("id"),
                username = a.getString("username"),
                auth = runCatching { AuthState.valueOf(a.getString("auth")) }.getOrDefault(AuthState.UNKNOWN),
                lastConnected = a.longOrNull("lastConnected"),
                lastError = a.stringOrNull("lastError"),
                credentialRef = a.optString("credentialRef", a.getString("id")),
            )
        }
        val profiles = j.getJSONArray("profiles").objects().mapNotNull { p ->
            runCatching {
                val parsed = ProfileImporter.parse(
                    name = p.getString("name"),
                    text = p.getString("config"),
                    isCustom = p.optBoolean("isCustom", false),
                )
                val probeObj = p.optJSONObject("probe")
                val probe = probeObj?.let { pr ->
                    val ips = pr.optJSONArray("healthy")?.let { arr ->
                        (0 until arr.length()).map { idx -> arr.getString(idx) }
                    }.orEmpty()
                    ProbeResult(
                        millis = pr.longOrNull("ms"),
                        checkedAt = pr.getLong("checked"),
                        method = pr.getString("method"),
                        error = pr.stringOrNull("error"),
                        healthy = ips,
                    )
                }
                parsed.copy(
                    favorite = p.optBoolean("favorite", false),
                    probe = probe,
                )
            }.onFailure { err ->
                ConnectionLogStore.append("Profiles", "Skipped stored profile: ${err.message}", isError = true)
            }.getOrNull()
        }
        return QobearCatalog(
            accounts = accounts,
            profiles = profiles,
            activeAccountId = j.stringOrNull("activeAccount"),
            selectedProfileId = j.stringOrNull("selectedProfile"),
        )
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.stringOrNull(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONObject.longOrNull(key: String) = if (isNull(key)) null else getLong(key)

    companion object {
        private const val VAULT_CATALOG = "catalog"
        private const val VAULT_COMPANIONS = "companions"

        @Volatile
        private var instance: QobearRepository? = null

        fun get(context: Context): QobearRepository =
            instance ?: synchronized(this) {
                instance ?: QobearRepository(context.applicationContext).also { instance = it }
            }
    }
}
