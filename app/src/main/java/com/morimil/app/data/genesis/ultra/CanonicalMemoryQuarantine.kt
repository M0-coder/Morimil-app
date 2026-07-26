package com.morimil.app.data.genesis.ultra

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class CanonicalMemoryQuarantineDiagnostic(
    val code: String,
    val stage: String,
    val sequence: Long?,
    val detail: String,
    val detectedAtMillis: Long
) {
    fun visibleMessage(): String {
        val sequenceLabel = sequence?.let { value -> ", secuencia $value" }.orEmpty()
        return "Morimil se abstuvo: la memoria canónica está en cuarentena " +
            "($code$sequenceLabel). Ningún contenido de esa cadena entró al prompt."
    }
}

internal class CanonicalMemoryQuarantinedException internal constructor(
    val diagnostic: CanonicalMemoryQuarantineDiagnostic,
    cause: Throwable
) : IllegalStateException(
    "canonical_memory_quarantined:${diagnostic.code}:${diagnostic.sequence ?: "unknown"}",
    cause
)

/**
 * Process-local projection of a durable integrity failure.
 *
 * The corrupted chain remains the evidence and will reproduce the quarantine
 * after restart. This registry never edits, repairs or deletes memory rows.
 */
internal object CanonicalMemoryQuarantineStore {
    private val _diagnostic = MutableStateFlow<CanonicalMemoryQuarantineDiagnostic?>(null)
    val diagnostic: StateFlow<CanonicalMemoryQuarantineDiagnostic?> = _diagnostic.asStateFlow()

    suspend fun <T> verify(
        stage: String,
        clockMillis: () -> Long = System::currentTimeMillis,
        block: suspend () -> T
    ): T {
        return try {
            val result = block()
            _diagnostic.value = null
            result
        } catch (error: CanonicalMemoryQuarantinedException) {
            throw error
        } catch (error: Throwable) {
            throw quarantine(stage, error, clockMillis())
        }
    }

    fun quarantineIfIntegrityFailure(
        stage: String,
        error: Throwable,
        detectedAtMillis: Long = System.currentTimeMillis()
    ): Throwable {
        if (error is CanonicalMemoryQuarantinedException) return error
        return if (isIntegrityFailure(error)) {
            quarantine(stage, error, detectedAtMillis)
        } else {
            error
        }
    }

    private fun quarantine(
        stage: String,
        error: Throwable,
        detectedAtMillis: Long
    ): CanonicalMemoryQuarantinedException {
        val raw = error.message?.trim().orEmpty()
        val code = raw.substringBefore(':')
            .takeIf { value -> value.matches(CODE) }
            ?: "canonical_memory_integrity_failure"
        val sequence = raw.substringAfter(':', "")
            .substringBefore(':')
            .toLongOrNull()
        val diagnostic = CanonicalMemoryQuarantineDiagnostic(
            code = code,
            stage = stage.take(80),
            sequence = sequence,
            detail = raw.ifBlank { error::class.java.simpleName }.take(240),
            detectedAtMillis = detectedAtMillis
        )
        _diagnostic.value = diagnostic
        return CanonicalMemoryQuarantinedException(diagnostic, error)
    }

    private fun isIntegrityFailure(error: Throwable): Boolean {
        val code = error.message?.substringBefore(':').orEmpty()
        return INTEGRITY_PREFIXES.any(code::startsWith)
    }

    internal fun clearForTest() {
        _diagnostic.value = null
    }

    private val CODE = Regex("^[a-z0-9_]{3,120}$")
    private val INTEGRITY_PREFIXES = listOf(
        "canonical_memory_source_",
        "canonical_memory_chain_",
        "canonical_memory_signature_",
        "canonical_memory_foreign_instance_",
        "canonical_memory_event_payload_",
        "canonical_memory_payload_event_",
        "canonical_memory_metadata_",
        "canonical_memory_recovered_root_",
        "persisted_birth_memory_root_"
    )
}
