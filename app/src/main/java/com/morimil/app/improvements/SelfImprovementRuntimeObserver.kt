package com.morimil.app.improvements

import android.content.Context
import java.io.File

/**
 * Process-local bridge from runtime health signals to the durable DETECTED audit.
 *
 * Initialization gives this observer only app-private file storage. Proposal UI
 * persistence remains lazy and auxiliary. This observer receives no Git, merge,
 * release, install or signing authority.
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
                collector = SelfImprovementSignalCollector(auditStore),
                proposalStore = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
                    ImprovementProposalStore(appContext)
                }
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
        runCatching {
            active.proposalStore.value.refreshObservedSignals(
                chatError = null,
                internalComponent = component,
                internalMessage = message,
                memoryNeedsAttention = false
            )
        }
    }

    fun reportChatError(error: String, occurredAtMillis: Long = System.currentTimeMillis()) {
        val active = state ?: return
        active.collector.captureChatError(error, occurredAtMillis)
        runCatching {
            active.proposalStore.value.refreshObservedSignals(
                chatError = error,
                internalComponent = null,
                internalMessage = null,
                memoryNeedsAttention = false
            )
        }
    }

    fun reportMemoryAttention(occurredAtMillis: Long = System.currentTimeMillis()) {
        val active = state ?: return
        active.collector.captureMemoryAttention(occurredAtMillis)
        runCatching {
            active.proposalStore.value.refreshObservedSignals(
                chatError = null,
                internalComponent = null,
                internalMessage = null,
                memoryNeedsAttention = true
            )
        }
    }

    fun readVerifiedAuditForDiagnostics(): List<SelfChangeAuditRecord> {
        return state?.auditStore?.readVerifiedRecords().orEmpty()
    }

    private data class State(
        val auditStore: SelfImprovementAuditStore,
        val collector: SelfImprovementSignalCollector,
        val proposalStore: Lazy<ImprovementProposalStore>
    )
}
