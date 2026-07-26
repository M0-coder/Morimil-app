package com.morimil.app.data.repository

data class LivingMemoryAppendRequest(
    val eventType: String,
    val actor: String,
    val body: String,
    val importance: Int,
    val evidenceJson: String? = null,
    val source: String = "system",
    val userConfirmed: Boolean = false,
    val eventId: String? = null
)

data class LivingMemoryEventReceipt(
    val eventHash: String,
    val sequence: Long,
    val eventType: String,
    val observedAtMillis: Long
)

data class LivingMemoryEventView(
    val eventHash: String,
    val sequence: Long,
    val eventType: String,
    val actor: String,
    val body: String,
    val observedAtMillis: Long
)

/** Product-level boundary. Implementations must never write `memory_events`. */
interface LivingMemoryPort {
    suspend fun append(request: LivingMemoryAppendRequest): LivingMemoryEventReceipt
    suspend fun loadLatestByType(eventType: String): LivingMemoryEventView?
}

object LegacyMemoryReadOnlyPort : LivingMemoryPort {
    override suspend fun append(request: LivingMemoryAppendRequest): LivingMemoryEventReceipt {
        error("legacy_memory_writer_not_configured")
    }

    override suspend fun loadLatestByType(eventType: String): LivingMemoryEventView? = null
}
