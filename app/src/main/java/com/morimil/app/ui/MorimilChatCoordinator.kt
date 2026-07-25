package com.morimil.app.ui

import android.app.Application
import com.morimil.app.MorimilAppContainer
import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.ReasoningClient
import com.morimil.app.data.genesis.GenesisIdentitySource
import com.morimil.app.data.local.LocalInstanceIdentityEntity
import com.morimil.app.data.local.ReasoningTurnAuthor
import com.morimil.app.data.local.ReasoningTurnEntity
import com.morimil.app.reasoning.ReasoningKernelRequest
import com.morimil.app.reasoning.model.ReasoningEscalationDecision
import com.morimil.app.reasoning.model.ReasoningEscalationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MorimilChatCoordinator(
    @Suppress("UNUSED_PARAMETER") application: Application,
    private val container: MorimilAppContainer,
    private val scope: CoroutineScope,
    private val localIdentity: StateFlow<LocalInstanceIdentityEntity?>,
    private val messages: StateFlow<List<ReasoningTurnEntity>>,
    private val observeTask: suspend (String, suspend () -> Unit) -> Result<Unit>
) {
    private val _genesisResult = MutableStateFlow<Result<GenesisIdentitySource>?>(null)
    val genesisResult: StateFlow<Result<GenesisIdentitySource>?> = _genesisResult.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private var cachedDoctrineText: String? = null
    private var cachedPolicyText: String? = null

    init {
        scope.launch {
            ReasoningEscalationStore.pendingRequest.collect { request ->
                if (request != null && request.decision != ReasoningEscalationDecision.PENDING) {
                    while (_isSending.value) {
                        delay(10)
                    }
                    val latest = ReasoningEscalationStore.pendingRequest.value
                    if (latest?.requestId == request.requestId &&
                        latest.decision != ReasoningEscalationDecision.PENDING
                    ) {
                        val task = ReasoningEscalationStore.taskForRequest(request.requestId)
                        if (task != null) {
                            sendMessageInternal(body = task, appendUserTurn = false)
                        }
                    }
                }
            }
        }
    }

    fun refreshGenesis() {
        scope.launch {
            val result = container.genesisReader.readGenesisIdentity()
            _genesisResult.value = result
            result.getOrNull()?.identity?.doctrineRef?.let { ref ->
                cachedDoctrineText = container.genesisReader.readDoctrineText(ref).getOrNull()
            }
            result.getOrNull()?.identity?.policyRef?.let { ref ->
                cachedPolicyText = container.genesisReader.readPolicyText(ref).getOrNull()
            }
        }
    }

    /**
     * Retained only as a fail-closed binary/source compatibility boundary.
     * Genesis Ultra birth must never install the legacy bundle or call
     * MemoryRepository.birthLocalIdentity().
     */
    suspend fun bornInstance(
        @Suppress("UNUSED_PARAMETER") alias: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Result.failure(
            IllegalStateException("legacy_local_birth_path_disabled_use_genesis_ultra")
        )
    }

    fun sendMessage(body: String) {
        sendMessageInternal(body = body, appendUserTurn = true)
    }

    private fun sendMessageInternal(body: String, appendUserTurn: Boolean) {
        val cleanBody = body.trim()
        if (cleanBody.isEmpty() || _isSending.value) return
        ReasoningEscalationStore.discardIfTaskChanged(cleanBody)

        scope.launch {
            _chatError.value = null

            val genesis = _genesisResult.value?.getOrNull()?.identity
            if (genesis == null) {
                _chatError.value = "Genesis no esta cargado todavia. Intenta de nuevo en un momento."
                return@launch
            }

            val configuredHelper = container.reasoningConfigStore.loadActiveHelper()
            val runtimeConfig = configuredHelper.config
            val runtimeAccess = if (runtimeConfig.requiresRuntimeKey) {
                container.secretVault.readReasoningKey(
                    slotId = configuredHelper.id,
                    endpoint = runtimeConfig.baseUrl
                ).getOrNull().orEmpty()
            } else {
                ""
            }
            val alias = localIdentity.value?.alias ?: genesis.alias

            _isSending.value = true
            try {
                val trustedTurns = messages.value
                    .filter { turn -> ReasoningTurnAuthor.isTrustedConversationAuthor(turn.author) }
                val lastTrustedTurn = trustedTurns.lastOrNull()
                val historyTurns = if (!appendUserTurn &&
                    lastTrustedTurn?.author == ReasoningTurnAuthor.USER &&
                    lastTrustedTurn.body.trim() == cleanBody
                ) {
                    trustedTurns.dropLast(1)
                } else {
                    trustedTurns
                }
                val priorHistory = historyTurns
                    .takeLast(ReasoningClient.MAX_HISTORY_MESSAGES - 1)
                    .map { turn ->
                        ChatTurn(
                            role = if (turn.author == ReasoningTurnAuthor.USER) "user" else "assistant",
                            content = turn.body
                        )
                    }

                val result = withContext(Dispatchers.IO) {
                    if (appendUserTurn) {
                        container.reasoningTranscriptRepository.appendUserTurn(cleanBody)
                    }
                    container.reasoningKernel.reason(
                        ReasoningKernelRequest(
                            input = cleanBody,
                            genesis = genesis,
                            alias = alias,
                            doctrineText = cachedDoctrineText,
                            policyText = cachedPolicyText,
                            priorHistory = priorHistory,
                            runtimeLabel = configuredHelper.displayName,
                            runtimeConfig = runtimeConfig,
                            runtimeAccess = runtimeAccess
                        )
                    )
                }

                result.morimilReply?.let { reply ->
                    withContext(Dispatchers.IO) {
                        container.reasoningTranscriptRepository.appendMorimilTurn(reply)
                    }
                }
                result.auxiliaryAdvisory?.let { advisory ->
                    withContext(Dispatchers.IO) {
                        container.reasoningTranscriptRepository.appendAuxiliaryAdvisoryTurn(
                            advisory.content
                        )
                    }
                }

                if (result.errorMessage != null) {
                    _chatError.value = result.errorMessage ?: "Error con el razonamiento."
                }
            } finally {
                ReasoningEscalationStore.clearResolvedFor(cleanBody)
                _isSending.value = false
            }
        }
    }
}
