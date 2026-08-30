package dev.mterm.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.TestOnly
import dev.mterm.agents.AgentActivity
import dev.mterm.agents.AgentProfile
import dev.mterm.changes.logs.AgentLogReader
import dev.mterm.git.GitCli
import dev.mterm.settings.MTermSettings
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class AgentChangeTracker(private val project: Project) : Disposable {

    interface Listener {
        fun changesUpdated()
    }

    private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("mTerm agent changes", 1)
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val live = LinkedHashMap<String, LiveSession>()
    private val records = LinkedHashMap<String, AgentSessionRecord>()
    private val pendingByRepo = HashMap<String, Set<String>>()
    private var loaded = false

    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (events.any { it.path.contains(GIT_DIRECTORY) }) scheduleRefresh()
                }
            },
        )
    }

    @Volatile
    private var published: List<AgentSessionRecord> = emptyList()

    fun sessions(): List<AgentSessionRecord> {
        if (!loaded) executor.execute { ensureLoaded() }
        return published
    }

    fun openSession(profile: AgentProfile, workingDirectory: String?): AgentSessionHandle? {
        if (!MTermSettings.getInstance().trackAgentChanges) return null
        val directory = workingDirectory ?: project.basePath ?: return null
        val sessionId = UUID.randomUUID().toString()
        val agentSessionId = if (AgentSessionCommand.acceptsSessionId(profile.command)) {
            UUID.randomUUID().toString()
        } else {
            null
        }
        executor.execute { register(sessionId, profile, Path.of(directory), agentSessionId) }
        return AgentSessionHandle(this, sessionId, agentSessionId)
    }

    fun refreshNow() {
        executor.execute {
            ensureLoaded()
            publish()
        }
    }

    private fun scheduleRefresh() {
        if (refreshAlarm.isDisposed) return
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest(::refreshNow, REFRESH_DELAY_MS)
    }

    fun clearHistory() {
        executor.execute {
            ensureLoaded()
            records.keys.retainAll(live.keys)
            AgentChangeStore.getInstance(project).save(emptyList())
            publish()
        }
    }

    internal fun activity(sessionId: String, activity: AgentActivity) {
        val at = System.currentTimeMillis()
        executor.execute {
            val session = live[sessionId] ?: return@execute
            when (activity) {
                AgentActivity.BUSY -> beginTurn(session, at)
                AgentActivity.ATTENTION, AgentActivity.IDLE -> finishTurn(session, at)
            }
        }
    }

    internal fun resetSession(sessionId: String) {
        executor.execute {
            val session = live[sessionId] ?: return@execute
            applyReset(session)
        }
    }

    internal fun title(sessionId: String, raw: String) {
        val clean = raw.replace(LEADING_SYMBOLS, "").replace(WHITESPACE, " ").trim()
        if (clean.isEmpty()) return
        executor.execute {
            val session = live[sessionId] ?: return@execute
            if (session.baseTree != null && session.title == null) session.title = clean.take(MAX_TITLE)
        }
    }

    internal fun closeSession(sessionId: String) {
        val at = System.currentTimeMillis()
        executor.execute {
            val session = live.remove(sessionId) ?: return@execute
            if (session.baseTree != null) finishTurn(session, at, closing = true)
            records[sessionId]?.let { records[sessionId] = it.copy(live = false) }
            if (records[sessionId]?.turns.isNullOrEmpty()) records.remove(sessionId)
            persist()
            publish()
        }
    }

    private fun register(sessionId: String, profile: AgentProfile, directory: Path, agentSessionId: String?) {
        ensureLoaded()
        val repoRoot = GitCli.repoRoot(directory) ?: return
        live[sessionId] = LiveSession(sessionId, profile, repoRoot, directory, agentSessionId)
        records[sessionId] = AgentSessionRecord(
            id = sessionId,
            agentId = profile.id,
            agentName = profile.displayName,
            glyph = profile.glyph,
            colorRgb = profile.color.rgb and 0xFFFFFF,
            repoRoot = repoRoot.toString(),
            startedAt = System.currentTimeMillis(),
            live = true,
            turns = emptyList(),
        )
        pruneRefs(repoRoot)
        publish()
    }

    private fun applyReset(session: LiveSession) {
        session.reset()
        val record = records[session.id] ?: return
        records[session.id] = record.copy(turns = emptyList(), startedAt = System.currentTimeMillis())
        persist()
        publish()
    }

    private fun beginTurn(session: LiveSession, at: Long) {
        if (session.consumeContextClear()) applyReset(session)
        if (session.baseTree != null) return
        val tree = GitCli.snapshot(session.repoRoot) ?: return
        session.baseTree = tree
        session.turnStartedAt = at
        session.title = null
    }

    private fun finishTurn(session: LiveSession, at: Long, closing: Boolean = false) {
        val base = session.baseTree ?: return
        val startedAt = session.turnStartedAt
        session.baseTree = null
        session.remember(startedAt, at)

        val result = GitCli.snapshot(session.repoRoot) ?: return
        if (result == base) return

        val diff = GitCli.diff(session.repoRoot, base, result)
        if (diff.isEmpty()) return

        val ownPaths = session.paths(startedAt, at)
        val overlapping = live.values.filter { it !== session && it.overlaps(startedAt, at) }
        val otherPaths = overlapping.flatMapTo(mutableSetOf()) { it.paths(startedAt, at) }

        val analysis = TurnAnalysis.analyse(
            repoRoot = session.repoRoot,
            diff = diff,
            ownPaths = ownPaths,
            otherPaths = otherPaths,
            overlapping = overlapping.isNotEmpty(),
        )
        if (analysis.changes.isEmpty()) return

        val record = records[session.id] ?: return
        session.ordinal += 1
        val turn = AgentTurn(
            id = UUID.randomUUID().toString(),
            ordinal = session.ordinal,
            title = session.title,
            startedAt = startedAt,
            finishedAt = at,
            baseTree = base,
            resultTree = result,
            changes = analysis.changes,
            attribution = analysis.attribution,
        )
        pin(session.repoRoot, base, at)
        pin(session.repoRoot, result, at)

        val turns = (record.turns + turn).takeLast(MAX_TURNS)
        records[session.id] = record.copy(turns = turns, live = !closing)
        persist()
        publish()
    }

    private fun pin(repo: Path, tree: String, at: Long) {
        GitCli.pinTree(repo, "$REF_PREFIX$at-${tree.take(REF_SHA_LENGTH)}", tree)
    }

    private fun pruneRefs(repo: Path) {
        val retention = MTermSettings.getInstance().changeRetentionDays.coerceAtLeast(1)
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retention.toLong())
        for (ref in GitCli.refs(repo, REF_PREFIX)) {
            val stamp = ref.removePrefix(REF_PREFIX).substringBefore('-').toLongOrNull() ?: continue
            if (stamp < cutoff) GitCli.deleteRef(repo, ref)
        }
        val stale = records.values.filter { !it.live && it.startedAt < cutoff }.map { it.id }
        if (stale.isNotEmpty()) {
            stale.forEach { records.remove(it) }
            persist()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        for (record in AgentChangeStore.getInstance(project).load()) records[record.id] = record
        publish()
    }

    private fun persist() {
        val history = records.values.toList().takeLast(MAX_SESSIONS)
        AgentChangeStore.getInstance(project).save(history.filter { it.turns.isNotEmpty() })
    }

    private fun refreshPending() {
        val repos = records.values.mapTo(mutableSetOf()) { it.repoRoot }
        pendingByRepo.keys.retainAll(repos)
        for (repo in repos) {
            val paths = GitCli.pendingPaths(Path.of(repo))
            if (paths == null) pendingByRepo.remove(repo) else pendingByRepo[repo] = paths
        }
    }

    private fun uncommitted(record: AgentSessionRecord): AgentSessionRecord? {
        val pending = pendingByRepo[record.repoRoot] ?: return record
        val turns = record.turns.mapNotNull { turn ->
            val changes = turn.changes.filter { it.path in pending }
            if (changes.isEmpty()) null else turn.copy(changes = changes)
        }
        return when {
            turns.isNotEmpty() -> record.copy(turns = turns)
            record.live -> record.copy(turns = emptyList())
            else -> null
        }
    }

    private fun publish() {
        refreshPending()
        published = records.values.reversed().mapNotNull { uncommitted(it) }
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            project.messageBus.syncPublisher(TOPIC).changesUpdated()
        }, project.disposed)
    }

    @TestOnly
    internal fun awaitIdle() {
        executor.submit { }.get(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    override fun dispose() {
        executor.shutdownNow()
    }

    private class LiveSession(
        val id: String,
        val profile: AgentProfile,
        val repoRoot: Path,
        val workingDirectory: Path,
        val agentSessionId: String?,
    ) {
        var baseTree: String? = null
        var turnStartedAt: Long = 0
        var title: String? = null
        var ordinal: Int = 0

        private val windows = ArrayDeque<LongRange>()
        private var reader: AgentLogReader? = null
        private var readerResolved = false
        private var lastResetCheck: Long = System.currentTimeMillis()

        fun reset() {
            baseTree = null
            turnStartedAt = 0
            title = null
            ordinal = 0
            windows.clear()
            reader = null
            readerResolved = false
            lastResetCheck = System.currentTimeMillis()
        }

        fun consumeContextClear(): Boolean {
            val log = reader ?: resolveReader() ?: return false
            val since = lastResetCheck
            lastResetCheck = System.currentTimeMillis()
            return runCatching { log.contextClearedSince(since) }.getOrDefault(false)
        }

        fun remember(from: Long, to: Long) {
            windows.addLast(from..to)
            while (windows.size > MAX_WINDOWS) windows.removeFirst()
        }

        fun overlaps(from: Long, to: Long): Boolean {
            val open = baseTree != null && turnStartedAt <= to
            return open || windows.any { it.first <= to && it.last >= from }
        }

        fun paths(from: Long, to: Long): Set<String> {
            val log = reader ?: resolveReader() ?: return emptySet()
            return runCatching { log.touchedPaths(from, to) }.getOrDefault(emptySet())
        }

        private fun resolveReader(): AgentLogReader? {
            if (readerResolved && reader != null) return reader
            val created = AgentLogReader.create(profile, agentSessionId, workingDirectory)
            if (created != null) {
                reader = created
                readerResolved = true
            }
            return created
        }

        private companion object {
            const val MAX_WINDOWS = 20
        }
    }

    companion object {
        @JvmStatic
        val TOPIC: Topic<Listener> = Topic.create("mTerm agent changes", Listener::class.java)

        private const val REF_PREFIX = "refs/mterm/"
        private const val REF_SHA_LENGTH = 10
        private const val MAX_TURNS = 100
        private const val MAX_SESSIONS = 40
        private const val MAX_TITLE = 60
        private const val AWAIT_TIMEOUT_SECONDS = 60L
        private const val REFRESH_DELAY_MS = 700
        private const val GIT_DIRECTORY = "/.git/"

        private val LEADING_SYMBOLS = Regex("^[^\\p{L}\\p{N}]+")
        private val WHITESPACE = Regex("\\s+")

        fun getInstance(project: Project): AgentChangeTracker = project.service()
    }
}

class AgentSessionHandle internal constructor(
    private val tracker: AgentChangeTracker,
    val sessionId: String,
    private val agentSessionId: String?,
) {

    private val input = StringBuilder()

    fun decorate(command: String?): String? = AgentSessionCommand.decorate(command, agentSessionId)

    fun onInput(text: String) {
        for (character in text) {
            when {
                character == '\r' || character == '\n' -> {
                    val line = input.toString()
                    input.setLength(0)
                    if (AgentSessionCommand.isContextReset(line)) tracker.resetSession(sessionId)
                }

                character == '\u007f' || character == '\b' -> if (input.isNotEmpty()) {
                    input.setLength(input.length - 1)
                }

                character.isISOControl() -> input.setLength(0)
                input.length < MAX_INPUT -> input.append(character)
            }
        }
    }

    fun onActivity(activity: AgentActivity) = tracker.activity(sessionId, activity)

    fun onTitle(raw: String) = tracker.title(sessionId, raw)

    fun close() = tracker.closeSession(sessionId)

    private companion object {
        const val MAX_INPUT = 200
    }
}
