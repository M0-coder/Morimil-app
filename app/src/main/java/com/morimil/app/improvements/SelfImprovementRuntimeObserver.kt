package com.morimil.app.improvements

import android.content.Context
import java.io.File

/**
 * Process-local bridge from runtime health signals to the durable DETECTED audit.
 *
 * Initialization gives this observer only app-private file storage and the
 * existing proposal store. It receives no Git, merge, release or signing authority.
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
                collector = SelfImprovementSignalCollector(auditStore),
                proposalStore = ImprovementProposalStore(appContext)
            )
        }
    }

    fun reportInternalRuntimeIssue(
        component: String,
        message: String,
        failureCount: Int,
        occurredAtMillis: Long
    ) {
        val active = state ?: return
        active.collector.captureInternalRuntimeIssue(
            component = component,
            message = message,
            failureCount = failureCount,
            occurredAtMillis = occurredAtMillis
        )
        active.proposalStore.refreshObservedSignals(
            chatError = null,
            internalComponent = component,
            internalMessage = message,
            memoryNeedsAttention = false
        )
    }

    fun reportChatError(error: String, occurredAtMillis: Long = System.currentTimeMillis()) {
        val active = state ?: return
        active.collector.captureChatError(error, occurredAtMillis)
        active.proposalStore.refreshObservedSignals(
            chatError = error,
            internalComponent = null,
            internalMessage = null,
            memoryNeedsAttention = false
        )
    }

    fun reportMemoryAttention(occurredAtMillis: Long = System.currentTimeMillis()) {
        val active = state ?: return
        active.collector.captureMemoryAttention(occurredAtMillis)
        active.proposalStore.refreshObservedSignals(
            chatError = null,
            internalComponent = null,
            internalMessage = null,
            memoryNeedsAttention = true
        )
    }

    fun readVerifiedAuditForDiagnostics(): List<SelfChangeAuditRecord> {
        val active = state ?: return emptyList()
        return active.auditStore().readVerifiedRecords()
    }

    private data class State(
        val collector: SelfImprovementSignalCollector,
        val proposalStore: ImprovementProposalStore
    ) {
        fun auditStore(): SelfImprovementAuditStore {
            val field = SelfImprovementSignalCollector::class.java.getDeclaredField("auditStore")
            field.isAccessible = true
            return field.get(collector) as SelfImprovementAuditStore
        }
    }
}
