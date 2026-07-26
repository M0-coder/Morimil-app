from pathlib import Path
import re

path = Path('app/src/main/java/com/morimil/app/data/genesis/ultra/LegacyMemoryConvergenceCoordinator.kt')
source = path.read_text()
source = source.replace('        val tipHash = events.lastOrNull()?.eventHash\n', '', 1)
pattern = re.compile(
    r'        if \(events\.isEmpty\(\)\) \{\n'
    r'            require\(localIdentityCount == 0 && genesisCoreCount == 0\) \{[\s\S]*?\n'
    r'            \}\n'
    r'            val complete = completeState\(',
    re.MULTILINE
)
replacement = '''        if (events.isEmpty()) {
    if (localIdentityCount != 0 || genesisCoreCount != 0) {
        block(
            identity = identity,
            events = events,
            dryRunDigest = dryRunDigest,
            failureCode = "legacy_convergence_identity_without_memory"
        )
        error("legacy_convergence_identity_without_memory")
    }
    val complete = completeState('''
source, count = pattern.subn(replacement, source, count=1)
if count != 1:
    raise SystemExit(f'coordinator empty lineage structural match={count}')
source = source.replace(
    '                verifyLegacyChain = memoryIntegrityCore::verifyMemoryEventChain,\n',
    '                verifyLegacyChain = { events ->\n'
    '                    memoryIntegrityCore.verifyMemoryEventChain(events)\n'
    '                },\n',
    1
)
source = source.replace(
    '                loadState = convergenceDao::loadState,\n',
    '                loadState = { convergenceDao.loadState() },\n',
    1
)
path.write_text(source)

path = Path('app/src/main/java/com/morimil/app/MorimilAppContainer.kt')
source = path.read_text()
pattern = re.compile(
    r'(MemoryRepository\(\s*'
    r'database = memoryDatabase,\s*'
    r'memoryIntegrityCore = memoryIntegrityCore,\s*)'
    r'memoryEventSigner = memoryEventSigner'
)
source, count = pattern.subn(
    r'\1livingMemoryPort = canonicalLivingMemoryPort',
    source,
    count=1
)
if count != 1:
    raise SystemExit(f'container memory writer structural match={count}')
source, count = re.subn(
    r'\n\s*memoryEventSigner = memoryEventSigner,',
    '',
    source,
    count=1
)
if count != 1:
    raise SystemExit(f'container rest signer match={count}')
path.write_text(source)

path = Path('app/src/main/java/com/morimil/app/data/repository/RestCycleRepository.kt')
source = path.read_text()
source = source.replace('import androidx.room.withTransaction\n', '')
source = source.replace('import com.morimil.app.core.memory.MemoryEventSigner\n', '')
source = source.replace('import com.morimil.app.data.repository.MemoryAppendGate\n', '')
source, count = re.subn(
    r'(private val memoryIntegrityCore: MemoryIntegrityCore,)\s*'
    r'private val memoryEventSigner: MemoryEventSigner,\s*'
    r'(private val memoryRepository: MemoryRepository)',
    r'\1\n    \2',
    source,
    count=1
)
if count != 1:
    raise SystemExit(f'rest cycle constructor structural match={count}')
source = source.replace(
    '        val latestRestCycle = memoryDao.loadLatestRestCycleEvent()\n',
    '        val latestRestCycle = memoryRepository.loadLatestLivingMemoryEventByType(REST_CYCLE_EVENT_TYPE)\n',
    1
)
source = source.replace(
    '            now - latestRestCycle.createdAtMillis < REST_CYCLE_MIN_INTERVAL_MILLIS\n',
    '            now - latestRestCycle.observedAtMillis < REST_CYCLE_MIN_INTERVAL_MILLIS\n',
    1
)
pattern = re.compile(
    r'    private suspend fun appendRestCycleEvent\([\s\S]*?\n'
    r'    \}\n\n'
    r'    private suspend fun consolidateAutobiographyFromRestCycle\('
)
replacement = '''    private suspend fun appendRestCycleEvent(
        summary: String,
        migrationId: String,
        approvalId: String?
    ): RestCycleAppendResult? {
        val genesisCore = requireNotNull(memoryDao.loadGenesisCore()) {
            "Cannot run rest cycle without a local Genesis Core."
        }
        val localIdentity = memoryDao.loadLocalIdentity()
        val createdAtMillis = System.currentTimeMillis()
        val evidenceJson = JSONObject()
            .put("schema", "morimil.memory_evidence.v1")
            .put("classifier", "local_rest_cycle_v1")
            .put("event_type", REST_CYCLE_EVENT_TYPE)
            .put("actor", "system")
            .put("source", "local_rest_cycle")
            .put("memory_kind", "rest_cycle")
            .put("user_confirmed", false)
            .put("confidence", 90)
            .put("migration_id", migrationId)
            .put("approval_id", approvalId)
            .put("excerpt", summary.take(240))
            .toString()
        val eventHash = memoryRepository.recordSystemMemoryEvent(
            eventType = REST_CYCLE_EVENT_TYPE,
            body = summary,
            importance = 88,
            evidenceJson = evidenceJson
        ) ?: return null
        return RestCycleAppendResult(
            eventHash = eventHash,
            instanceId = localIdentity?.instanceId ?: "legacy_instance_read_only",
            genesisCoreHash = genesisCore.contentSha256,
            createdAtMillis = createdAtMillis
        )
    }

    private suspend fun consolidateAutobiographyFromRestCycle('''
source, count = pattern.subn(replacement, source, count=1)
if count != 1:
    raise SystemExit(f'rest cycle canonical append structural match={count}')
path.write_text(source)
