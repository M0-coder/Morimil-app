package com.morimil.app.improvements

import android.content.Context
import java.io.File

/**
 * Process-local bridge from runtime health signals to the durable DETECTED audit.
 *
 * Initialization gives this observer only app-private file storage. This bridge
 * deliberately does not open Room or persist UI proposals, preventing a failure
 * in a primary store/signing path from recursively re-entering that same store.
 * It receives no Git, merge, release, install or signing authority.
 */
internal object SelfImprovementRuntimeObserver {
    private val lock = Any()

    @Volatile
    private var state: State? = null

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (state != null) return
            val auditStore = SelfImprovementAuditStore(
                File(appContext.filesDir, SelfImprovementAuditStore.DEFAULT_RELATIVE_PATH)
            )
            // Fail closed on existing audit corruption before enabling new capture.
            auditStore.readVerifiedRecords()
            state = State(
                auditStore = auditStore,
                collector = SelfImprovementSignalCollector(auditStore)
            )
        }
    }

    fun reportInternalRuntimeIssue(
        component: String,
        message: String,
        failureCount: Int,
        occurredAtMillis: Long
    ) {
        state?.collector?.captureInternalRuntimeIssue(
            component = component,
            message = message,
            failureCount = failureCount,
            occurredAtMillis = occurredAtMillis
        )
    }

    fun reportChatError(error: String, occurredAtMillis: Long = System.currentTimeMillis()) {
        state?.collector?.captureChatError(error, occurredAtMillis)
    }

    fun reportMemoryAttention(occurredAtMillis: Long = System.currentTimeMillis()) {
        state?.collector?.captureMemoryAttention(occurredAtMillis)
    }

    fun readVerifiedAuditForDiagnostics(): List<SelfChangeAuditRecord> {
        return state?.auditStore?.readVerifiedRecords().orEmpty()
    }

    private data class State(
        val auditStore: SelfImprovementAuditStore,
        val collector: SelfImprovementSignalCollector
    )
}
