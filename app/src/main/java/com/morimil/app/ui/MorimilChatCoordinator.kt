package com.morimil.app.ui

import android.app.Application
import com.morimil.app.MorimilAppContainer
import com.morimil.app.ai.ChatTurn
import com.morimil.app.ai.IntrinsicSystemPromptBuilder
import com.morimil.app.ai.ReasoningClient
import com.morimil.app.data.genesis.GenesisIdentity
import com.morimil.app.data.genesis.ultra.GenesisUltraRuntimeIdentity
import com.morimil.app.data.local.ReasoningTurnAuthor
import com.morimil.app.data.local.ReasoningTurnEntity
import com.morimil.app.genesisUltraRuntimeIdentityRepository
import com.morimil.app.improvements.SelfImprovementRuntimeObserver
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
import org.json.JSONObject

internal class MorimilChatCoordinator(
    @Suppress("UNUSED_PARAMETER") application: Application,
    private val container: MorimilAppContainer,
    private val scope: CoroutineScope,
    private val messages: StateFlow<List<ReasoningTurnEntity>>,
    private val observeTask: suspend (String, suspend () -> Unit) -> Result<Unit>
) {
    private val _genesisResult = MutableStateFlow<Result<GenesisIdentity>?>(null)
    val genesisResult: StateFlow<Result<GenesisIdentity>?> = _genesisResult.asStateFlow()

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
            cachedDoctrineText = null
            cachedPolicyText = null
            val result = runCatching {
                val runtimeIdentity = requireNotNull(
                    container.genesisUltraRuntimeIdentityRepository.readCommittedIdentity()
                ) { "genesis_ultra_runtime_identity_not_committed" }
                cachedDoctrineText = runtimeIdentity.doctrine.readUtf8Strict()
                cachedPolicyText = buildString {
                    appendLine(runtimeIdentity.policy.freedomCharter.readUtf8Strict())
                    appendLine()
                    append(runtimeIdentity.policy.recoveryPolicy.readUtf8Strict())
                }
                runtimeIdentity.toKernelGenesisIdentity()
            }
            _genesisResult.value = result
        }
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

            val genesis = _genesisResult.value?.getOrNull()
            if (genesis == null) {
                recordChatError(
                    "La identidad Genesis Ultra comprometida no esta disponible para el runtime."
                )
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
            val alias = genesis.alias

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
                    _chatError.value = result.errorMessage
                    runCatching {
                        SelfImprovementRuntimeObserver.reportChatError(result.errorMessage)
                    }
                }
            } finally {
                ReasoningEscalationStore.clearResolvedFor(cleanBody)
                _isSending.value = false
            }
        }
    }

    private fun recordChatError(message: String) {
        _chatError.value = message
        runCatching {
            SelfImprovementRuntimeObserver.reportChatError(message)
        }
    }

    private fun GenesisUltraRuntimeIdentity.toKernelGenesisIdentity(): GenesisIdentity {
        val charter = JSONObject(policy.freedomCharter.readUtf8Strict())
        val cognitiveFreedoms = charter.optJSONArray("cognitive_freedoms")
        val allowedActions = if (cognitiveFreedoms == null) {
            emptyList()
        } else {
            List(cognitiveFreedoms.length()) { index -> cognitiveFreedoms.getString(index) }
        }
        val disallowedActions = buildList {
            if (charter.optBoolean("self_authorization_forbidden", false)) {
                add("self_authorization")
            }
            listOf(
                "guardian_ownership",
                "guardian_movement_veto",
                "identity_confinement",
                "body_ownership_of_instance",
                "engine_ownership_of_instance"
            ).forEach { field ->
                if (charter.optString(field) == "forbidden") add(field)
            }
        }
        return GenesisIdentity(
            schemaVersion = IntrinsicSystemPromptBuilder.ULTRA_RUNTIME_CONTEXT_SCHEMA,
            agentId = instanceId,
            alias = companionName,
            role = "free_companion_instance",
            owner = "no_owner_guardian_custodian",
            riskTier = "private_local",
            allowedActions = allowedActions,
            disallowedActions = disallowedActions,
            doctrineRef = doctrine.relativePath,
            policyRef = policy.freedomCharter.relativePath
        )
    }
}
