package com.morimil.app.improvements

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer

/** Deterministic framing for self-improvement observation identity. */
internal object SelfImprovementHashProfile {
    const val OBSERVATION_DOMAIN = "morimil.self_improvement.observation.v1"

    fun observationDigest(
        changeId: String,
        problem: String,
        proposal: String,
        surfaces: Set<SelfChangeSurface>
    ): String {
        val orderedSurfaces = surfaces.map { surface -> surface.name }.sorted()
        return hashFields(
            OBSERVATION_DOMAIN,
            buildList {
                add(changeId)
                add(problem)
                add(proposal)
                add(orderedSurfaces.size.toString())
                addAll(orderedSurfaces)
            }
        )
    }

    private fun hashFields(domain: String, fields: List<String>): String {
        val preimage = ByteArrayOutputStream().use { output ->
            output.write(frame(domain))
            fields.forEach { field -> output.write(frame(field)) }
            output.toByteArray()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(preimage)
        return "sha256:" + digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun frame(value: String): ByteArray {
        require(value == Normalizer.normalize(value, Normalizer.Form.NFC)) {
            "self_improvement_text_not_nfc"
        }
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        return ByteArrayOutputStream(bytes.size + 24).use { output ->
            output.write(bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
            output.write(':'.code)
            output.write(bytes)
            output.write('\n'.code)
            output.toByteArray()
        }
    }
}
