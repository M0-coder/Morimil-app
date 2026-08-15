package com.morimil.app.data.genesis.ultra

/**
 * Body-independent Genesis Ultra Instance identifier profile.
 *
 * The permanent Instance identity must never be derived from a Body identifier,
 * platform profile, Android identifier, package certificate, keystore alias,
 * model, provider or database key. A Body is bound after the Instance identifier
 * exists and remains replaceable through writer-epoch succession.
 */
internal object GenesisUltraInstanceIdProfile {
    const val DOMAIN = "genesis.instance.id.v0.2"

    fun derive(
        releaseRoot: String,
        companionName: String,
        bornAt: String,
        entropyRef: String
    ): String {
        GenesisUltraHashProfile.requireNfc(companionName)
        return "inst_" + GenesisUltraHashProfile.hashFields(
            DOMAIN,
            listOf(
                releaseRoot,
                companionName,
                bornAt,
                entropyRef
            )
        ).removePrefix("sha256:")
    }
}
