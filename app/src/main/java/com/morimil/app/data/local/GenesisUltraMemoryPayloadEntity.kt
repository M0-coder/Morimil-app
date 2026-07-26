package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Recoverable private payload bound one-to-one to a signed Genesis Ultra event.
 *
 * The signed event remains the authority. This row only stores bytes whose
 * digests are already committed by that event. No update or delete DAO exists.
 */
@Entity(
    tableName = "genesis_ultra_memory_payloads",
    primaryKeys = ["eventHash"],
    indices = [
        Index(
            value = ["instanceId", "sequence"],
            unique = true,
            name = "index_genesis_ultra_memory_payloads_instanceId_sequence"
        ),
        Index(
            value = ["contentDigest"],
            name = "index_genesis_ultra_memory_payloads_contentDigest"
        )
    ]
)
data class GenesisUltraMemoryPayloadEntity(
    val eventHash: String,
    val instanceId: String,
    val sequence: Long,
    val contentDigest: String,
    val contentType: String,
    val contentByteCount: Long,
    val contentBytes: ByteArray,
    val provenanceDigest: String,
    val provenanceType: String,
    val provenanceByteCount: Long,
    val provenanceBytes: ByteArray,
    val privacy: String,
    val persistedAtMillis: Long
)
