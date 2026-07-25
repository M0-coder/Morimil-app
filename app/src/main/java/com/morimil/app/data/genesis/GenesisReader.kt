package com.morimil.app.data.genesis

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Reads the bundled legacy Genesis material for migration analysis.
 *
 * The embedded bundle is non-authoritative and read-only. It cannot be installed
 * as a new birth source; canonical birth belongs exclusively to Genesis Ultra.
 */
class GenesisReader(private val context: Context) {
    private val manifestVerifier = GenesisManifestVerifier(context)

    suspend fun readGenesisIdentity(): Result<GenesisIdentitySource> = withContext(Dispatchers.IO) {
        runCatching {
            val verification = manifestVerifier.verify()
            GenesisIdentitySource(
                identity = readBundled(),
                origin = GenesisOrigin.BUNDLED_SEED,
                manifest = verification
            )
        }
    }

    suspend fun readDoctrineText(doctrineRef: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { readBundledText(doctrineRef) }
    }

    suspend fun readPolicyText(policyRef: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { readBundledText(policyRef) }
    }

    suspend fun clearInstalledGenesisBundle(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            File(context.filesDir, "genesis_staging").deleteRecursively()
            File(context.filesDir, "genesis").deleteRecursively()
            Unit
        }
    }

    private fun readBundled(): GenesisIdentity {
        val rawJson = context.assets
            .open(GENESIS_ASSET)
            .bufferedReader()
            .use { it.readText() }
        return parseIdentity(JSONObject(rawJson))
    }

    private fun readBundledText(ref: String): String {
        val cleanRef = ref.trim().removePrefix("/")
        require(!cleanRef.contains("..")) { "Genesis bundle ref cannot escape assets/genesis." }
        return context.assets
            .open("$GENESIS_ROOT/$cleanRef")
            .bufferedReader()
            .use { it.readText() }
    }

    private fun parseIdentity(root: JSONObject): GenesisIdentity {
        return GenesisIdentity(
            schemaVersion = root.getString("schema_version"),
            agentId = root.getString("agent_id"),
            alias = root.getString("alias"),
            role = root.getString("role"),
            owner = root.getString("owner"),
            riskTier = root.getString("risk_tier"),
            allowedActions = root.getJSONArray("allowed_actions").toStringList(),
            disallowedActions = root.getJSONArray("disallowed_actions").toStringList(),
            doctrineRef = root.getString("doctrine_ref"),
            policyRef = root.getString("policy_ref")
        )
    }

    private fun JSONArray.toStringList(): List<String> {
        return List(length()) { index -> getString(index) }
    }

    companion object {
        private const val GENESIS_ROOT = "genesis"
        private const val GENESIS_ASSET = "$GENESIS_ROOT/identity/orchestrator.identity.json"
    }
}
