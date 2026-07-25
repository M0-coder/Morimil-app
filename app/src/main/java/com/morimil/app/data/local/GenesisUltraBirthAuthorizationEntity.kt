package com.morimil.app.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Immutable evidence that one exact candidate and host consent authorized the
 * primary Genesis Ultra birth. No update DAO is exposed for this record.
 */
@Entity(
    tableName = "genesis_ultra_birth_authorization",
    primaryKeys = ["slotId"],
    indices = [
        Index(
            value = ["candidateDigest"],
            unique = true,
            name = "index_genesis_ultra_birth_authorization_candidateDigest"
        ),
        Index(
            value = ["consentDigest"],
            unique = true,
            name = "index_genesis_ultra_birth_authorization_consentDigest"
        ),
        Index(
            value = ["authorizationDigest"],
            unique = true,
            name = "index_genesis_ultra_birth_authorization_authorizationDigest"
        )
    ]
)
data class GenesisUltraBirthAuthorizationEntity(
    val slotId: String,
    val schemaVersion: String,
    val candidateDigest: String,
    val consentDigest: String,
    val birthStateDigest: String,
    val receiptDigest: String,
    val bodyId: String,
    val guardianId: String,
    val guardianKeyEpochId: String,
    val authorizedAt: String,
    val expiresAt: String,
    val authorizationDigest: String,
    val sourceDigest: String,
    val sourceBytes: ByteArray
)
