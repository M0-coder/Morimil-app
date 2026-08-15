package com.morimil.app.improvements

import android.content.Context
import java.io.File

enum class SelfImprovementRuntimeStatus {
    NOT_INITIALIZED,
    READY,
    DEGRADED_AUDIT_UNAVAILABLE,
    DISABLED
}

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

    @Volatile
    private var status: SelfImprovementRuntimeStatus = SelfImprovementRuntimeStatus.NOT_INITIALIZED

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        install(
            SelfImprovementAuditStore(
                File(appContext.filesDir, SelfImprovementAuditStore.DEFAULT_RELATIVE_PATH)
            )
        )
    }

    internal fun initializeForTest(auditFile: File) {
        install(SelfImprovementAuditStore(auditFile))
    }

    internal fun resetForTest() {
        synchronized(lock) {
            state = null
            status = SelfImprovementRuntimeStatus.NOT_INITIALIZED
        }
    }

    fun runtimeStatus(): SelfImprovementRuntimeStatus = status

    private fun install(auditStore: SelfImprovementAuditStore) {
        synchronized(lock) {
            if (state != null) {
                status = SelfImprovementRuntimeStatus.READY
                return
            }
            try {
                // Fail closed on existing audit corruption/rollback before enabling capture.
                auditStore.readVerifiedRecords()
                state = State(
                    auditStore = auditStore,
                    collector = SelfImprovementSignalCollector(auditStore)
                )
                status = SelfImprovementRuntimeStatus.READY
            } catch (failure: Throwable) {
                degrade()
                throw failure
            }
        }
    }

    fun reportInternalRuntimeIssue(
        component: String,
        message: String,
        failureCount: Int,
        occurredAtMillis: Long
    ) {
        withReadyState { current ->
            current.collector.captureInternalRuntimeIssue(
                component = component,
                message = message,
                failureCount = failureCount,
                occurredAtMillis = occurredAtMillis
            )
        }
    }

    fun reportChatError(error: String, occurredAtMillis: Long = System.currentTimeMillis()) {
        withReadyState { current ->
            current.collector.captureChatError(error, occurredAtMillis)
        }
    }

    fun reportMemoryAttention(occurredAtMillis: Long = System.currentTimeMillis()) {
        withReadyState { current ->
            current.collector.captureMemoryAttention(occurredAtMillis)
        }
    }

    fun readVerifiedAuditForDiagnostics(): List<SelfChangeAuditRecord> {
        val current = state ?: return emptyList()
        return try {
            current.auditStore.readVerifiedRecords()
        } catch (failure: Throwable) {
            synchronized(lock) {
                if (state === current) degrade()
            }
            throw failure
        }
    }

    private inline fun withReadyState(block: (State) -> Unit) {
        val current = state ?: return
        try {
            block(current)
        } catch (failure: Throwable) {
            synchronized(lock) {
                if (state === current) degrade()
            }
            throw failure
        }
    }

    private fun degrade() {
        state = null
        status = SelfImprovementRuntimeStatus.DEGRADED_AUDIT_UNAVAILABLE
    }

    private data class State(
        val auditStore: SelfImprovementAuditStore,
        val collector: SelfImprovementSignalCollector
    )
}
