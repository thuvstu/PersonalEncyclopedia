package com.thuvstu.personalencyclopedia.db

import com.thuvstu.personalencyclopedia.db.dao.*
import com.thuvstu.personalencyclopedia.db.entity.*
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * ★wt57: 高校全科目シードの共通エンジン。
 * 科目ごとのデータは [SubjectSeed] として別ファイル（HsKobun / HsKanbun / HsEnglish / HsChemistry /
 * HsPhysics / HsBiology / HsGeography / HsHistory / HsPolitics / HsEconomics / HsEthics /
 * HsInformatics / HsLaw / HsMisc）に置き、ここでは投入ロジックだけを持つ。
 *
 * 冪等性: エントリーはタイトル一致、クイズは設問文、Wiki はタイトル、接続は重複判定、
 * ホワイトボードはタイトル一致でそれぞれスキップ。InitialDataMath.seedAppend と同じ規約。
 * センチネルは [SENTINEL]（古文の「係り結びの法則」。DemoData / InitialData / InitialDataMath に無い）。
 */
object InitialDataHighSchool {

    const val SENTINEL = "係り結びの法則"

    data class Def(val term: String, val reading: String?, val definition: String, val field: String, val topicId: String)
    data class Quiz(val q: String, val a: String, val choices: List<String>, val exp: String, val type: String = "mcq")
    data class Conn(val a: String, val b: String, val type: String, val strength: Float = 0.85f, val note: String = "")
    /** ★wt58 種付箋: 起点カードに最初から貼っておく「疑問・つながり・広げどころ」。[[リンク]]を含み、増殖の入口になる */
    data class Sticky(val card: String, val text: String, val color: String = "yellow")

    /**
     * 1科目分のシード。
     * @param rootTopicId 思考エントリー・クイズを紐づけるトピック
     * @param boardHubs   ホワイトボードのセクション（タイトル, カード化する定義タイトル最大4件, 色）
     */
    data class SubjectSeed(
        val key: String,
        val name: String,
        val rootTopicId: String,
        val topics: List<TopicEntity>,
        val definitions: List<Def>,
        val thoughts: List<DemoData.DemoThought> = emptyList(),
        val quizzes: List<Quiz> = emptyList(),
        val connections: List<Conn> = emptyList(),
        val tags: Map<String, List<String>> = emptyMap(),
        val wikis: List<WikiArticleEntity> = emptyList(),
        val boardTitle: String? = null,
        val boardHubs: List<Triple<String, List<String>, String>> = emptyList(),
        val stickies: List<Sticky> = emptyList()
    )

    data class Result(val added: Int, val skipped: Int, val connections: Int, val quizzes: Int, val stickies: Int = 0) {
        operator fun plus(o: Result) = Result(added + o.added, skipped + o.skipped, connections + o.connections, quizzes + o.quizzes, stickies + o.stickies)
    }

    /** ★wt58 自動相互参照: 定義本文中に他のシード済みタイトルが出てきたら references 接続を張る。1カードあたりの上限 */
    const val AUTO_REF_CAP = 6
    const val AUTO_REF_MIN_TITLE_LEN = 4

    /**
     * 純粋関数: 本文 [definition] が言及している他タイトルを、誤爆を除いて返す（上限 [cap]）。
     * 除外: 自分自身 / 自分と包含関係のタイトル（「評論」⊂「評論文の読み方」）/ 短すぎるタイトル。
     * [titles] は呼び出し側で長さ降順に並べておくと、長い（具体的な）タイトルが優先される。
     */
    fun mentionedTitles(term: String, definition: String, titles: List<String>, cap: Int = AUTO_REF_CAP): List<String> {
        val out = ArrayList<String>(cap)
        for (t in titles) {
            if (out.size >= cap) break
            if (t.length < AUTO_REF_MIN_TITLE_LEN) continue
            if (t == term || term.contains(t) || t.contains(term)) continue
            if (!definition.contains(t)) continue
            out += t
        }
        return out
    }

    /** 純粋関数: クイズ本文（設問+正答+解説）に最長一致するタイトル。 [titlesByLengthDesc] は長さ降順 */
    fun bestTitleForQuiz(question: String, answer: String, explanation: String?, titlesByLengthDesc: List<String>): String? {
        val hay = question + "\n" + answer + "\n" + (explanation ?: "")
        return titlesByLengthDesc.firstOrNull { it.length >= 2 && hay.contains(it) }
    }

    /** 全科目。順序 = ユーザー指定順。 */
    val allSubjects: List<SubjectSeed>
        get() = listOf(
            HsKobun.seed, HsKanbun.seed, HsEnglish.seed, HsChemistry.seed, HsPhysics.seed, HsBiology.seed,
            HsGeography.seed, HsHistory.seed, HsPolitics.seed, HsEconomics.seed, HsEthics.seed,
            HsInformatics.seed, HsLaw.seed, HsMisc.seed,
            HsDeepAncient.seed, HsDeepEgypt.seed  // ★wt59 深掘り講義（大学水準）
        )

    suspend fun seedAppend(
        entryDao: EntryDao,
        thoughtDao: EntryThoughtDao,
        definitionDao: EntryDefinitionDao,
        tagDao: TagDao?,
        topicDao: TopicDao?,
        quizDao: QuizDao?,
        connectionDao: ConnectionDao?,
        whiteboardDao: WhiteboardDao?,
        wikiDao: WikiArticleDao?,
        subjects: List<SubjectSeed> = allSubjects,
        stickyDao: EntryStickyNoteDao? = null
    ): Result {
        var total = Result(0, 0, 0, 0)
        for (s in subjects) {
            total += seedSubject(s, entryDao, thoughtDao, definitionDao, tagDao, topicDao, quizDao, connectionDao, whiteboardDao, wikiDao)
        }
        // ★wt58 データ強化: 科目横断ブリッジ接続 + 本文言及の自動 references + 種付箋
        if (connectionDao != null) {
            val bridgeSeed = SubjectSeed(
                key = "bridges", name = "科目横断", rootTopicId = "topic-misc",
                topics = emptyList(), definitions = emptyList(), connections = HsStickies.bridges
            )
            total += seedSubject(bridgeSeed, entryDao, thoughtDao, definitionDao, null, null, null, connectionDao, null, null)
            total += Result(0, 0, seedAutoReferences(subjects, entryDao, connectionDao), 0)
        }
        if (stickyDao != null) total += Result(0, 0, 0, 0, seedStickies(subjects, entryDao, stickyDao))
        if (quizDao != null) linkQuizzesToCards(subjects, entryDao, quizDao)
        return total
    }

    /** ★wt58: 本体投入済みDBに、ブリッジ接続・自動references・種付箋だけを冪等追記する */
    suspend fun seedEnrichmentOnly(
        entryDao: EntryDao, thoughtDao: EntryThoughtDao, definitionDao: EntryDefinitionDao,
        connectionDao: ConnectionDao, stickyDao: EntryStickyNoteDao, subjects: List<SubjectSeed> = allSubjects,
        quizDao: QuizDao? = null
    ): Result {
        if (quizDao != null) linkQuizzesToCards(subjects, entryDao, quizDao)
        val bridgeSeed = SubjectSeed(
            key = "bridges", name = "科目横断", rootTopicId = "topic-misc",
            topics = emptyList(), definitions = emptyList(), connections = HsStickies.bridges
        )
        var total = seedSubject(bridgeSeed, entryDao, thoughtDao, definitionDao, null, null, null, connectionDao, null, null)
        total += Result(0, 0, seedAutoReferences(subjects, entryDao, connectionDao), 0)
        total += Result(0, 0, 0, 0, seedStickies(subjects, entryDao, stickyDao))
        return total
    }

    /**
     * 定義本文に「他の定義タイトル」が現れたら a→b の references を張る（本文が相手に言及している）。
     * 全14科目のタイトルを対象にするので科目をまたぐ（例: 保健「感染症」→生物「免疫」）。
     * 短いタイトル（3文字以下）は誤爆するので除外。既存接続があればスキップ。冪等。
     */
    suspend fun seedAutoReferences(subjects: List<SubjectSeed>, entryDao: EntryDao, connectionDao: ConnectionDao): Int {
        val defs = subjects.flatMap { it.definitions }
        val titles = defs.map { it.term }.filter { it.length >= AUTO_REF_MIN_TITLE_LEN }.distinct().sortedByDescending { it.length }
        val idCache = HashMap<String, String?>()
        suspend fun idOf(t: String): String? = idCache.getOrPut(t) { entryDao.findByTitle(t)?.id }
        var made = 0
        for (d in defs) {
            val src = idOf(d.term) ?: continue
            for (t in mentionedTitles(d.term, d.definition, titles)) {
                val dst = idOf(t) ?: continue
                if (dst == src) continue
                if (connectionDao.countDirectedDuplicate(src, dst, "references") > 0) continue
                if (connectionDao.countUndirectedDuplicate(minOf(src, dst), maxOf(src, dst), "related") > 0) continue
                try {
                    val r = connectionDao.insert(
                        ConnectionEntity(
                            entryAId = src, entryBId = dst, relationType = "references", strength = 0.6f,
                            note = "本文が言及", isAuto = false, isDirected = true,
                            canonicalA = minOf(src, dst), canonicalB = maxOf(src, dst)
                        )
                    )
                    if (r > 0) made++
                } catch (_: Exception) {}
            }
        }
        return made
    }

    /**
     * ★wt58: 出典なしのシードクイズ（generationMethod=initial, sourceEntryId=null）を、
     * 設問文/正答/解説に最も長く一致する定義タイトルのカードへ紐づける。
     * これで「カード詳細 → このカードのクイズ」「クイズ → カードへ戻る」が成立する。冪等（紐づけ済みは対象外）。
     */
    suspend fun linkQuizzesToCards(subjects: List<SubjectSeed>, entryDao: EntryDao, quizDao: QuizDao): Int {
        val titles = (subjects.flatMap { it.definitions }.map { it.term } + HsStickies.extraLinkTitles)
            .filter { it.length >= 2 }.distinct().sortedByDescending { it.length }
        val pending = quizDao.getUnlinkedInitial()
        if (pending.isEmpty()) return 0
        val idCache = HashMap<String, String?>()
        var linked = 0
        for (q in pending) {
            val hit = bestTitleForQuiz(q.question, q.answer, q.explanation, titles) ?: continue
            val eid = idCache.getOrPut(hit) { entryDao.findByTitle(hit)?.id } ?: continue
            quizDao.setSourceEntry(q.id, eid)
            linked++
        }
        return linked
    }

    /** 種付箋を貼る。同じカードに同じ本文が既にあればスキップ（冪等）。 */
    suspend fun seedStickies(subjects: List<SubjectSeed>, entryDao: EntryDao, stickyDao: EntryStickyNoteDao): Int {
        var made = 0
        val all = subjects.flatMap { it.stickies } + HsStickies.common
        for (st in all) {
            val eid = entryDao.findByTitle(st.card)?.id ?: continue
            val existing = stickyDao.getByEntryId(eid)
            if (existing.any { it.text == st.text }) continue
            stickyDao.insert(
                EntryStickyNoteEntity(
                    entryId = eid, text = st.text, color = st.color, source = "seed",
                    sortOrder = stickyDao.nextSortOrder(eid)
                )
            )
            made++
        }
        return made
    }

    private fun jsonArr(items: List<String>) =
        "[" + items.joinToString(",") { "\"" + it.replace("\"", "\\\"") + "\"" } + "]"

    suspend fun seedSubject(
        s: SubjectSeed,
        entryDao: EntryDao,
        thoughtDao: EntryThoughtDao,
        definitionDao: EntryDefinitionDao,
        tagDao: TagDao?,
        topicDao: TopicDao?,
        quizDao: QuizDao?,
        connectionDao: ConnectionDao?,
        whiteboardDao: WhiteboardDao?,
        wikiDao: WikiArticleDao?
    ): Result {
        val now = System.currentTimeMillis()
        var added = 0
        var skipped = 0
        val idMap = mutableMapOf<String, String>()

        s.topics.forEach { topicDao?.insert(it) }

        suspend fun ensure(title: String, type: String, topicId: String?, content: String? = null, insertExt: suspend (String) -> Unit): String {
            entryDao.findByTitle(title)?.let { existing ->
                idMap[title] = existing.id; skipped++; return existing.id
            }
            val id = UUID.randomUUID().toString()
            entryDao.insert(
                EntryEntity(
                    id = id, type = type, title = title, content = content,
                    createdAt = now, updatedAt = now, accessedAt = now
                )
            )
            insertExt(id)
            if (topicId != null) {
                try { topicDao?.linkEntryTopic(EntryTopicEntity(entryId = id, topicId = topicId)) } catch (_: Exception) {}
            }
            idMap[title] = id; added++
            return id
        }

        for (d in s.definitions) {
            ensure(d.term, "definition", d.topicId) { id ->
                definitionDao.insert(
                    EntryDefinitionEntity(
                        entryId = id, term = d.term, reading = d.reading,
                        definition = d.definition, field = d.field
                    )
                )
            }
        }
        for (t in s.thoughts) {
            ensure(t.title, "thought", s.rootTopicId, content = t.content) { id ->
                thoughtDao.insert(EntryThoughtEntity(entryId = id))
            }
        }

        if (tagDao != null) for ((title, names) in s.tags) {
            val eid = idMap[title] ?: continue
            for (name in names) {
                val tag = tagDao.getByName(name) ?: run {
                    val t = TagEntity(name = name); tagDao.insert(t); tagDao.getByName(name) ?: t
                }
                try { tagDao.linkTag(EntryTagEntity(entryId = eid, tagId = tag.id)) } catch (_: Exception) {}
            }
        }

        var quizAdded = 0
        if (quizDao != null) for (q in s.quizzes) {
            if (quizDao.countByQuestion(q.q) > 0) continue
            quizDao.insertQuiz(
                QuizBankEntity(
                    question = q.q, answer = q.a, choicesJson = jsonArr(q.choices),
                    explanation = q.exp, quizType = q.type, generationMethod = "initial",
                    topicId = s.rootTopicId, difficulty = 3
                )
            )
            quizAdded++
        }

        if (wikiDao != null) for (w in s.wikis) {
            if (wikiDao.findByTitle(w.title) == null) wikiDao.upsert(w)
        }

        var conns = 0
        if (connectionDao != null) {
            suspend fun resolve(title: String): String? =
                idMap[title] ?: entryDao.findByTitle(title)?.id?.also { idMap[title] = it }
            for (c in s.connections) {
                val a = resolve(c.a) ?: continue
                val b = resolve(c.b) ?: continue
                if (a == b) continue
                val typeDef = connectionDao.getTypeDef(c.type)
                val directed = typeDef?.isDirected ?: (c.type != "related")
                val ca = if (a < b) a else b
                val cb = if (a < b) b else a
                val dup = if (directed) connectionDao.countDirectedDuplicate(a, b, c.type) > 0
                else connectionDao.countUndirectedDuplicate(ca, cb, c.type) > 0
                if (dup) continue
                try {
                    val r = connectionDao.insert(
                        ConnectionEntity(
                            entryAId = a, entryBId = b, relationType = c.type, strength = c.strength,
                            note = c.note.ifBlank { null }, isAuto = false, isDirected = directed,
                            canonicalA = ca, canonicalB = cb
                        )
                    )
                    if (r > 0) conns++
                } catch (_: Exception) {}
            }
        }

        val boardTitle = s.boardTitle
        if (boardTitle != null && whiteboardDao != null && s.boardHubs.isNotEmpty() &&
            whiteboardDao.observeBoards().first().none { it.title == boardTitle }
        ) {
            val boardId = UUID.randomUUID().toString()
            whiteboardDao.upsertBoard(
                WhiteboardEntity(
                    id = boardId, title = boardTitle,
                    summary = "${s.name} の単元マップ。カードは定義エントリー",
                    createdAt = now, updatedAt = now
                )
            )
            for ((idx, hub) in s.boardHubs.withIndex()) {
                val (title, spokes, color) = hub
                val col = idx % 3
                val row = idx / 3
                val cx = 40f + col * 720f
                val cy = 20f + row * 560f
                val sectionId = UUID.randomUUID().toString()
                whiteboardDao.upsertSection(
                    WhiteboardSectionEntity(
                        id = sectionId, boardId = boardId, title = title,
                        x = cx, y = cy, width = 680f, height = 520f, colorHex = color
                    )
                )
                val positions = listOf(
                    cx + 40f to cy + 60f, cx + 360f to cy + 60f,
                    cx + 40f to cy + 280f, cx + 360f to cy + 280f
                )
                val nodeIds = mutableListOf<String>()
                for ((sIdx, spoke) in spokes.take(4).withIndex()) {
                    val eid = idMap[spoke] ?: entryDao.findByTitle(spoke)?.id ?: continue
                    val (x, y) = positions[sIdx]
                    val node = WhiteboardNodeEntity(
                        boardId = boardId, entryId = eid, x = x, y = y,
                        width = 240f, height = 110f, sectionId = sectionId
                    )
                    whiteboardDao.upsertNode(node)
                    nodeIds += node.id
                }
                if (nodeIds.size >= 2) {
                    whiteboardDao.upsertEdge(
                        WhiteboardEdgeEntity(
                            boardId = boardId, sourceNodeId = nodeIds[0], targetNodeId = nodeIds[1],
                            label = "関連", createdAt = now
                        )
                    )
                }
            }
        }

        return Result(added = added, skipped = skipped, connections = conns, quizzes = quizAdded)
    }
}
