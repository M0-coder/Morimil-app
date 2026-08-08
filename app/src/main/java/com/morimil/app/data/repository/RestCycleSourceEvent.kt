package com.morimil.app.data.repository

internal data class RestCycleSourceEvent(
    val eventHash: String,
    val eventType: String,
    val actor: String,
    val source: String,
    val memoryKind: String,
    val tagsJson: String,
    val body: String,
    val importance: Int,
    val confidence: Int,
    val userConfirmed: Boolean,
    val observedAtMillis: Long
)
