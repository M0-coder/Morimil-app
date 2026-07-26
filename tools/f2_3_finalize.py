from pathlib import Path
import re

path = Path('app/src/main/java/com/morimil/app/runtime/GenesisUltraRuntimeBootstrapCoordinator.kt')
source = path.read_text()
source = source.replace(
    'import com.morimil.app.data.local.AgentProfileEntity\n',
    'import com.morimil.app.data.local.AgentProfileEntity\n'
    'import com.morimil.app.data.local.LegacyMemoryConvergenceEntity\n',
    1
)
source, count = re.subn(
    r'(val canonicalMemoryEventCount: Int,\n\s*)val healthState:',
    r'\1val legacyMemoryConverged: Boolean,\n    val healthState:',
    source,
    count=1
)
if count != 1:
    raise SystemExit(f'bootstrap report field match={count}')
source, count = re.subn(
    r'\s*require\(legacyCounts\.isEmpty\) \{ "runtime_bootstrap_legacy_rows_present" \}',
    '\n        require(legacyCounts.isEmpty || legacyMemoryConverged) {\n'
    '            "runtime_bootstrap_legacy_rows_not_converged"\n'
    '        }',
    source,
    count=1
)
if count != 1:
    raise SystemExit(f'bootstrap report invariant match={count}')
source, count = re.subn(
    r'private val countCanonicalMemoryEvents: suspend \(\) -> Int\n\)',
    'private val countCanonicalMemoryEvents: suspend () -> Int,\n'
    '    private val isLegacyMemoryConverged: suspend (String) -> Boolean = { false }\n)',
    source,
    count=1
)
if count != 1:
    raise SystemExit(f'bootstrap constructor dependency match={count}')

pattern = re.compile(
    r'    suspend fun bootstrap\([\s\S]*?\n'
    r'    \}\n\n'
    r'    internal companion object'
)
replacement = '''    suspend fun bootstrap(
        identity: GenesisUltraRuntimeIdentity,
        nowMillis: Long = System.currentTimeMillis()
    ): GenesisUltraRuntimeBootstrapReport {
        val before = inspectLegacyCounts()
        val convergedBefore = before.isEmpty || isLegacyMemoryConverged(identity.instanceId)
        require(convergedBefore) { "runtime_bootstrap_legacy_identity_conflict" }

        val projection = writeRuntimeProjection(identity, nowMillis)
        val orchestration = seedOrchestration(identity, nowMillis)
        val canonicalMemoryEventCount = countCanonicalMemoryEvents()

        val after = inspectLegacyCounts()
        val convergedAfter = after.isEmpty || isLegacyMemoryConverged(identity.instanceId)
        require(convergedAfter) { "runtime_bootstrap_created_unconverged_legacy_identity" }

        return GenesisUltraRuntimeBootstrapReport(
            instanceId = identity.instanceId,
            companionName = identity.companionName,
            workspaceId = projection.workspaceId,
            projectId = projection.projectId,
            agentProfileCount = orchestration.agentProfileCount,
            orchestratorDeviceCount = orchestration.orchestratorDeviceCount,
            canonicalMemoryEventCount = canonicalMemoryEventCount,
            legacyMemoryConverged = convergedAfter,
            healthState = GenesisUltraRuntimeSubsystemState.READY,
            restCycleState = GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER,
            recallState = GenesisUltraRuntimeSubsystemState.WAITING_FOR_CANONICAL_MEMORY_ADAPTER,
            legacyCounts = after
        )
    }

    internal companion object'''
source, count = pattern.subn(replacement, source, count=1)
if count != 1:
    raise SystemExit(f'bootstrap method match={count}')

production_pattern = re.compile(
    r'(countCanonicalMemoryEvents = \{\s*'
    r'memoryDatabase\.genesisUltraMemoryDao\(\)\.countAll\(\)\s*'
    r'\})\s*\n\s*\)'
)
production_replacement = '''\1,
                isLegacyMemoryConverged = { instanceId ->
                    val state = memoryDatabase.legacyMemoryConvergenceDao().loadState()
                    state?.instanceId == instanceId &&
                        state.status == LegacyMemoryConvergenceEntity.STATUS_COMPLETE &&
                        state.activeWriter == LegacyMemoryConvergenceEntity.WRITER_GENESIS_ULTRA &&
                        state.legacyReadOnly &&
                        state.failureCode == null
                }
            )'''
source, count = production_pattern.subn(production_replacement, source, count=1)
if count != 1:
    raise SystemExit(f'bootstrap production convergence match={count}')

source, count = re.subn(
    r'(countCanonicalMemoryEvents: suspend \(\) -> Int)\n(\s*\): GenesisUltraRuntimeBootstrapCoordinator)',
    r'\1,\n            isLegacyMemoryConverged: suspend (String) -> Boolean = { false }\n\2',
    source,
    count=1
)
if count != 1:
    raise SystemExit(f'bootstrap test factory signature match={count}')
source, count = re.subn(
    r'(countCanonicalMemoryEvents = countCanonicalMemoryEvents)\n(\s*\))',
    r'\1,\n                isLegacyMemoryConverged = isLegacyMemoryConverged\n\2',
    source,
    count=1
)
if count != 1:
    raise SystemExit(f'bootstrap test factory wiring match={count}')
path.write_text(source)

migration_files = [
    Path('app/src/androidTest/java/com/morimil/app/data/local/DatabaseMigrationTest.kt'),
    Path('app/src/androidTest/java/com/morimil/app/data/local/FullChainDatabaseMigrationTest.kt'),
    Path('app/src/androidTest/java/com/morimil/app/data/local/MorimilDatabaseMigrationTest.kt'),
    Path('app/src/androidTest/java/com/morimil/app/data/local/MorimilDatabaseV12ToV14MigrationTest.kt'),
]
for migration_path in migration_files:
    text = migration_path.read_text()
    text = text.replace('To14', 'To15').replace('to14', 'to15')
    text = text.replace('CurrentV14', 'CurrentV15')
    text = text.replace('v14', 'v15')
    text = text.replace(
        '            14,\n            true,',
        '            15,\n            true,'
    )
    text, count = re.subn(
        r'(?m)^(\s*)MorimilDatabase\.MIGRATION_13_14\s*$',
        r'\1MorimilDatabase.MIGRATION_13_14,\n\1MorimilDatabase.MIGRATION_14_15',
        text,
        count=1
    )
    if count != 1:
        raise SystemExit(f'migration 14_15 registration missing:{migration_path}')
    text = text.replace('assertEquals(14,', 'assertEquals(15,')
    migration_path.write_text(text)

old_path = Path('app/src/androidTest/java/com/morimil/app/data/local/MorimilDatabaseV12ToV14MigrationTest.kt')
new_path = Path('app/src/androidTest/java/com/morimil/app/data/local/MorimilDatabaseV12ToV15MigrationTest.kt')
old_path.rename(new_path)
