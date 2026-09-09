package com.thuvstu.personalencyclopedia.repository

import com.thuvstu.personalencyclopedia.brain.ai.EmbeddingQueue
import com.thuvstu.personalencyclopedia.brain.connection.ConnectionEngine
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.ConnectionDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryStickyNoteDao
import com.thuvstu.personalencyclopedia.db.dao.TaskDao
import com.thuvstu.personalencyclopedia.db.entity.ConnectionCandidateEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryStickyNoteEntity
import com.thuvstu.personalencyclopedia.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 付箋のライフサイクル一元化（wt56）。
 *
 * すべての画面(一覧/詳細/SRS/クイズ/白板/Wiki/API)はここを経由する。
 * 付箋の追加・編集・削除は entry の検索文書(FTS/意味検索)を再構築する
 * (`EmbeddingQueue.enqueue` → `EmbeddingTextBuilder` が付箋本文を連結する)。
 *
 * 昇格は3方向。いずれも骨格「自動でconnectionへ書かない」を守る:
 *  - 思考エントリ化: 付箋→ thought entry。元付箋は promotedEntryId を持ち解決済みにする
 *  - タスク化: 付箋→ task(linkedEntryId=元entry)。締切・見積は呼び出し側が指定
 *  - 接続候補化: 付箋→ connection_candidate(pending)。承認は既存フロー
 */
@Singleton
class StickyNoteRepository @Inject constructor(
    private val dao: EntryStickyNoteDao,
    private val entryDao: EntryDao,
    private val entryRepo: EntryRepository,
    private val taskDao: TaskDao,
    private val connectionDao: ConnectionDao,
    private val embeddingQueue: EmbeddingQueue,
    private val definitionDao: EntryDefinitionDao,          // ★wt58: 分野継承
    private val connectionEngine: ConnectionEngine          // ★wt58: 直接接続
) {
    companion object {
        const val SOURCE_DETAIL = "detail"
        const val SOURCE_LIST = "list"
        const val SOURCE_SRS = "srs"
        const val SOURCE_QUIZ = "quiz"
        const val SOURCE_WHITEBOARD = "whiteboard"
        const val SOURCE_WIKI = "wiki"
        const val SOURCE_API = "api"

        private val WIKI_LINK = Regex("""\[\[([^\[\]|]+)(?:\|[^\]]*)?]]""")

        /** 付箋本文から [[wiki-link]] のタイトル一覧を抜く（純粋関数） */
        fun extractWikiLinks(text: String): List<String> =
            WIKI_LINK.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()

        /** 昇格時のタイトル: 先頭行を最大40字 */
        fun titleFrom(text: String): String {
            val first = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "付箋"
            return if (first.length <= 40) first else first.take(39) + "…"
        }
    }

    /** 直近に削除した付箋（Undo用・メモリ保持のみ） */
    @Volatile private var lastDeleted: EntryStickyNoteEntity? = null

    // ── 監視 ──
    fun observeForEntry(entryId: String): Flow<List<EntryStickyNoteEntity>> = dao.observeByEntryId(entryId)
    fun observeAllGrouped(): Flow<Map<String, List<EntryStickyNoteEntity>>> =
        dao.observeAll().map { list -> list.groupBy { it.entryId } }
    fun observeRecentUnresolved(limit: Int = 10): Flow<List<EntryStickyNoteEntity>> = dao.observeRecentUnresolved(limit)
    fun observeUnresolvedCount(): Flow<Int> = dao.observeUnresolvedCount()
    fun observeEntryIdsWithNotes(): Flow<Set<String>> = dao.observeEntryIdsWithNotes().map { it.toSet() }

    suspend fun getForEntry(entryId: String): List<EntryStickyNoteEntity> = dao.getByEntryId(entryId)
    suspend fun getById(id: String): EntryStickyNoteEntity? = dao.getById(id)

    // ── CRUD ──
    suspend fun add(
        entryId: String,
        text: String,
        color: String = "yellow",
        source: String = SOURCE_DETAIL,
        contextId: String? = null
    ): EntryStickyNoteEntity? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val note = EntryStickyNoteEntity(
            entryId = entryId,
            text = t,
            color = color,
            source = source,
            contextId = contextId,
            sortOrder = dao.nextSortOrder(entryId)
        )
        dao.insert(note)
        reindex(entryId)
        return note
    }

    suspend fun updateText(note: EntryStickyNoteEntity, text: String, color: String = note.color) {
        val t = text.trim()
        if (t.isEmpty()) return
        dao.update(note.copy(text = t, color = color, updatedAt = System.currentTimeMillis()))
        reindex(note.entryId)
    }

    /** 削除。直前1件はUndo可能 */
    suspend fun delete(note: EntryStickyNoteEntity) {
        lastDeleted = note
        dao.deleteById(note.id)
        reindex(note.entryId)
    }

    /** 直前の削除を取り消す。取り消せた付箋を返す */
    suspend fun undoDelete(): EntryStickyNoteEntity? {
        val n = lastDeleted ?: return null
        lastDeleted = null
        dao.insert(n)
        reindex(n.entryId)
        return n
    }

    suspend fun setPinned(note: EntryStickyNoteEntity, pinned: Boolean) =
        dao.setPinned(note.id, pinned, System.currentTimeMillis())

    suspend fun setResolved(note: EntryStickyNoteEntity, resolved: Boolean) {
        val now = System.currentTimeMillis()
        dao.setResolved(note.id, resolved, if (resolved) now else null, now)
    }

    /** 1つ上/下へ移動（同一entry内）。並び順は observeByEntryId と同じ基準で計算する */
    suspend fun move(note: EntryStickyNoteEntity, up: Boolean) {
        val list = dao.getByEntryId(note.entryId)
        val idx = list.indexOfFirst { it.id == note.id }
        if (idx < 0) return
        val swapIdx = if (up) idx - 1 else idx + 1
        if (swapIdx !in list.indices) return
        val other = list[swapIdx]
        // ピン/解決の境界をまたぐ移動はしない（表示順の意味が変わるため）
        if (other.isPinned != note.isPinned || other.isResolved != note.isResolved) return
        val now = System.currentTimeMillis()
        // sortOrder が同値の場合に備え、全件を現在の表示順で採番し直してから入れ替える
        val reordered = list.toMutableList().also { it[idx] = other; it[swapIdx] = note }
        reordered.forEachIndexed { i, n -> if (n.sortOrder != i) dao.setSortOrder(n.id, i, now) }
    }

    // ── 昇格 ──

    /** 付箋 → 思考エントリ。作成したentryIdを返す。既に昇格済みならそのIDを返す */
    suspend fun promoteToThought(note: EntryStickyNoteEntity): String {
        note.promotedEntryId?.let { return it }
        val parent = entryDao.getById(note.entryId)
        val body = buildString {
            append(note.text)
            if (parent != null) {
                append("\n\n---\n")
                append("元カード: [[").append(parent.title).append("]]")
            }
        }
        val newId = entryRepo.createThought(
            ThoughtDraft(
                title = titleFrom(note.text),
                content = body,
                context = "sticky_note:${note.id}"
            )
        )
        val now = System.currentTimeMillis()
        dao.setPromotedEntry(note.id, newId, now)
        dao.setResolved(note.id, true, now, now)
        return newId
    }

    /** 付箋 → タスク。既に昇格済みならそのIDを返す */
    suspend fun promoteToTask(
        note: EntryStickyNoteEntity,
        estimatedMinutes: Int,
        deadlineAt: Long
    ): String {
        note.promotedTaskId?.let { return it }
        val parent = entryDao.getById(note.entryId)
        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = titleFrom(note.text),
            description = buildString {
                append(note.text)
                if (parent != null) append("\n\n(付箋元: ").append(parent.title).append(")")
            },
            estimatedMinutes = estimatedMinutes.coerceAtLeast(1),
            deadlineAt = deadlineAt,
            linkedEntryId = note.entryId
        )
        taskDao.insert(task)
        val now = System.currentTimeMillis()
        dao.setPromotedTask(note.id, task.id, now)
        dao.setResolved(note.id, true, now, now)
        return task.id
    }

    /**
     * 付箋 → 接続候補(pending)。付箋本文に [[タイトル]] が含まれていればそれを相手にする。
     * 相手を明示指定も可。成功時は candidateId、相手が解決できなければ null。
     * 承認制の骨格に従い connection へは直接書かない。
     */
    suspend fun promoteToConnectionCandidate(
        note: EntryStickyNoteEntity,
        targetEntryId: String? = null
    ): String? {
        note.promotedCandidateId?.let { return it }
        val target: EntryEntity = when {
            targetEntryId != null -> entryDao.getById(targetEntryId)
            else -> extractWikiLinks(note.text).firstNotNullOfOrNull { entryDao.findByTitle(it) }
        } ?: return null
        if (target.id == note.entryId) return null
        // 既に同ペアの候補があれば IGNORE される(unique index)。その場合は既存を探す
        val candidate = ConnectionCandidateEntity(
            entryAId = note.entryId,
            entryBId = target.id,
            similarity = 1.0f,        // 人手由来なので最上位に並べる
            suggestedType = "related",
            status = "pending"
        )
        val row = connectionDao.insertCandidate(candidate)
        val candidateId = if (row != -1L) candidate.id else {
            // 逆向き/同向きの既存候補を探す
            connectionDao.observePendingCandidates().first().firstOrNull {
                (it.entryAId == note.entryId && it.entryBId == target.id) ||
                    (it.entryAId == target.id && it.entryBId == note.entryId)
            }?.id ?: return null
        }
        dao.setPromotedCandidate(note.id, candidateId, System.currentTimeMillis())
        return candidateId
    }

    // ── ★wt58 増殖: 付箋から新カード・リンク・接続を生やす ──

    /** 付箋本文の [[リンク]] を解決した結果。entry が null なら未作成のタイトル */
    data class LinkTarget(val title: String, val entry: EntryEntity?)

    /** 付箋内の [[タイトル]] を全部解決する（存在/未作成の両方を返す） */
    suspend fun resolveLinks(note: EntryStickyNoteEntity): List<LinkTarget> =
        extractWikiLinks(note.text).map { t -> LinkTarget(t, entryDao.findByTitle(t)?.takeIf { it.deletedAt == null }) }

    /** エディタ補完用: タイトル前方/部分一致の候補（自分自身は除く） */
    suspend fun suggestTitles(prefix: String, excludeEntryId: String? = null, limit: Int = 8): List<EntryEntity> {
        val q = prefix.trim()
        if (q.isEmpty()) return emptyList()
        return entryDao.suggestByTitle(q, limit + 1).filter { it.id != excludeEntryId }.take(limit)
    }

    /**
     * 付箋 → 定義カード。見出し=先頭行、本文=付箋全文、分野=元カードの分野を継承。
     * 元カード→新カードへ `extends`(派生) の接続を **直接** 張る（ユーザーの明示操作なので承認不要）。
     * 新カードには付箋がまた貼れるので、ここから連鎖的に広げられる。既に昇格済みならそのIDを返す。
     */
    suspend fun promoteToDefinition(note: EntryStickyNoteEntity, relationType: String = "extends"): String {
        note.promotedEntryId?.let { return it }
        val parent = entryDao.getById(note.entryId)
        val parentDef = definitionDao.getByEntryId(note.entryId)
        val term = titleFrom(note.text)
        val body = buildString {
            append(note.text.trim())
            if (parent != null) append("\n\n（元カード: [[").append(parent.title).append("]]）")
        }
        val newId = entryRepo.createDefinition(
            DefinitionDraft(term = term, definition = body, field = parentDef?.field)
        )
        connectionEngine.createManualConnection(note.entryId, newId, relationType, strength = 0.7f, note = "付箋から派生")
        val now = System.currentTimeMillis()
        dao.setPromotedEntry(note.id, newId, now)
        dao.setResolved(note.id, true, now, now)
        reindex(newId)
        return newId
    }

    /**
     * 付箋内の未作成 [[タイトル]] からスタブ定義カードを作り、元カードと接続する。
     * 既にそのタイトルのカードがあれば作らずに接続だけ張る。作成/既存カードのIDを返す。
     */
    suspend fun createCardFromLink(note: EntryStickyNoteEntity, title: String, relationType: String = "related"): String {
        val t = title.trim()
        val existing = entryDao.findByTitle(t)?.takeIf { it.deletedAt == null }
        val targetId = existing?.id ?: run {
            val parent = entryDao.getById(note.entryId)
            val parentDef = definitionDao.getByEntryId(note.entryId)
            val id = entryRepo.createDefinition(
                DefinitionDraft(
                    term = t,
                    // 既存シードと同じ骨格をテンプレとして置く。埋めるべき欄が見える＝次の行動が決まる
                    definition = "【定義】（ここに1文で）\n【体系】（上位概念・下位概念・対比相手）\n【例】（具体例を1つ）\n\n---\n発端の付箋: " + note.text.trim() +
                        (if (parent != null) "\n元カード: [[" + parent.title + "]]" else ""),
                    field = parentDef?.field
                )
            )
            reindex(id)
            id
        }
        if (targetId != note.entryId) {
            connectionEngine.createManualConnection(note.entryId, targetId, relationType, strength = 0.6f, note = "付箋の[[リンク]]から")
        }
        return targetId
    }

    /**
     * 付箋 → 接続を **直接** 作る（候補を経由しない）。相手を明示するか、[[リンク]]先の1件目を使う。
     * 成功時 connectionId。相手不明/重複なら null。付箋には promotedCandidateId の代わりに解決済みフラグだけ付ける。
     */
    suspend fun connectNow(
        note: EntryStickyNoteEntity,
        targetEntryId: String? = null,
        relationType: String = "related"
    ): String? {
        val target: EntryEntity = when {
            targetEntryId != null -> entryDao.getById(targetEntryId)
            else -> extractWikiLinks(note.text).firstNotNullOfOrNull { entryDao.findByTitle(it) }
        } ?: return null
        if (target.id == note.entryId) return null
        val id = connectionEngine.createManualConnection(
            note.entryId, target.id, relationType, strength = 0.7f, note = note.text.take(80)
        ) ?: return null
        val now = System.currentTimeMillis()
        dao.setResolved(note.id, true, now, now)
        return id
    }

    /** 付箋を別カードに **複製** して貼る（同じ疑問を関連カード側にも残したい時）。 */
    suspend fun copyTo(note: EntryStickyNoteEntity, targetEntryId: String): EntryStickyNoteEntity? {
        if (targetEntryId == note.entryId) return null
        val parent = entryDao.getById(note.entryId)
        val text = note.text + (if (parent != null) "\n（[[" + parent.title + "]] から）" else "")
        return add(targetEntryId, text, note.color, note.source, note.contextId)
    }

    // ── 検索連動 ──
    private suspend fun reindex(entryId: String) {
        try { embeddingQueue.enqueue(entryId) } catch (_: Exception) { /* 起動時差分で追いつく */ }
    }

    /** 付箋本文の直接検索（結果はentryにまとめて返す） */
    suspend fun searchNotes(q: String, limit: Int = 50): List<Pair<EntryStickyNoteEntity, EntryEntity>> {
        val query = q.trim()
        if (query.isEmpty()) return emptyList()
        return dao.searchText(query, limit).mapNotNull { n ->
            entryDao.getById(n.entryId)?.takeIf { it.deletedAt == null }?.let { n to it }
        }
    }
}
