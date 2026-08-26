package com.thuvstu.personalencyclopedia.db

import com.thuvstu.personalencyclopedia.db.dao.*
import com.thuvstu.personalencyclopedia.db.entity.*
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.Random

object DemoData {

    data class DemoDefinition(val term: String, val reading: String?, val definition: String, val field: String?)
    data class DemoThought(val title: String, val content: String)
    data class DemoQuiz(val question: String, val answer: String, val choices: List<String>, val explanation: String, val quizType: String = "mcq")

    val topics = listOf(
        TopicEntity(id = "topic-history", name = "歴史", colorHex = "#F59E0B"),
        TopicEntity(id = "topic-cs", name = "CS", colorHex = "#3B82F6"),
        TopicEntity(id = "topic-science", name = "理科", colorHex = "#10B981"),
        TopicEntity(id = "topic-economy", name = "経済", colorHex = "#8B5CF6")
    )

    val definitions = listOf(
        DemoDefinition("明治維新", "めいじいしん", "1868年に始まった、江戸幕府の統治から天皇中心の近代国家への政治・社会変革。廃藩置県・四民平等・富国強兵などの改革が行われた。", "歴史"),
        DemoDefinition("関ヶ原の戦い", "せきがはらのたたかい", "1600年、徳川家康率いる東軍と石田三成率いる西軍が美濃国関ヶ原で戦った合戦。東軍の勝利により徳川氏の覇権が確立した。", "歴史"),
        DemoDefinition("光合成", "こうごうせい", "植物や藻類が光エネルギーを利用して二酸化炭素と水からグルコースを合成し、酸素を放出する過程。葉緑体のチラコイドで行われる。", "理科"),
        DemoDefinition("需要と供給", "じゅきょうときょうきゅう", "市場経済において、買い手が求める量（需要）と売り手が提供する量（供給）の関係。価格はこの両者の均衡点で決まる。", "経済"),
        DemoDefinition("ハッシュテーブル", null, "キーと値のペアを格納するデータ構造。ハッシュ関数でキーをインデックスに変換し、平均O(1)で参照できる。衝突処理にはチェイン法やオープンアドレス法がある。", "CS"),
        DemoDefinition("再帰", "さいき", "関数が自分自身を呼び出すことによって問題を解く手法。基底ケース（終了条件）と再帰ケースの定義が必要。", "CS"),
    )

    val thoughts = listOf(
        DemoThought("学習システムを7回作り直してわかったこと", "完璧な設計より、毎日使える小さなシステムの方が価値がある。入力の摩擦を最小化することが、継続の最大のコツ。"),
        DemoThought("百科事典のように知識を育てたい", "点の知識が線で繋がり、やがて面になる。そのための「接続」の仕組みが、このアプリの心臓部になるはず。"),
    )

    val quizzes = listOf(
        DemoQuiz("明治維新が始まった年はいつか？", "1868年", listOf("1868年", "1600年", "1889年", "1912年"), "1868年に明治に改元され、近代化改革が本格化しました。"),
        DemoQuiz("関ヶ原の戦いにおける東軍の指揮官は誰か？", "徳川家康", listOf("徳川家康", "石田三成", "豊臣秀吉", "織田信長"), "東軍は徳川家康、西軍は石田三成が実質的な指揮をとりました。"),
        DemoQuiz("光合成において放出される気体はどれか？", "酸素", listOf("酸素", "二酸化炭素", "窒素", "水素"), "光合成では二酸化炭素を取り込み、酸素を放出します。"),
        DemoQuiz("ハッシュテーブルの平均的な参照時間複雑度はどれか？", "O(1)", listOf("O(1)", "O(N)", "O(log N)", "O(N^2)"), "ハッシュ関数によってキーから即座にインデックスを計算するため、平均O(1)です。"),
        DemoQuiz("関ヶ原の戦いが起きた西暦年はいつか？", "1600年", listOf("1600年", "1582年", "1603年", "1573年"), "1600年に美濃国関ヶ原で合戦が行われました。"),
        DemoQuiz("需要と供給が一致する価格を何と呼ぶか？", "均衡価格", listOf("均衡価格", "市場価格", "標準価格", "固定価格"), "需要曲線と供給曲線の交点で決まる価格を均衡価格と呼びます。"),
        DemoQuiz("再帰関数において処理を終了させるために必須の条件は何か？", "基底ケース", listOf("基底ケース", "ループ条件", "例外処理", "無限ループ"), "基底ケース（Base Case）がないと無限再帰になりスタックオーバーフローが発生します。"),
        DemoQuiz("植物の葉緑体で光合成の明反応が行われる場所はどこか？", "チラコイド", listOf("チラコイド", "ストロマ", "ミトコンドリア", "リボソーム"), "チラコイド膜上で光エネルギーの吸収と化学エネルギーへの変換が行われます。"),
        DemoQuiz("江戸幕府から明治政府への政治的権力移譲のきっかけとなった出来事は何か？", "大政奉還", listOf("大政奉還", "廃藩置県", "王政復古の号令", "戊辰戦争"), "1867年10月の徳川慶喜による大政奉還が直接のきっかけです。"),
        DemoQuiz("ハッシュ値の衝突を解決する代表的な手法はどれか？", "チェイン法", listOf("チェイン法", "二分探索法", "クイックソート", "ダイクストラ法"), "衝突時に連結リストで繋ぐチェイン法や、空き領域を探すオープンアドレス法が使われます。")
    )

    // ── ホワイトボード用デモ: 歴史とCSの2ボード ──
    data class DemoBoard(val title: String, val summary: String)
    val demoBoards = listOf(
        DemoBoard("歴史探求ボード", "江戸〜明治の転換点を地図と年表で整理。Heptabase風のセクションで時代を俯瞰"),
        DemoBoard("CS学習ボード", "ハッシュテーブルと再帰を中心に、データ構造の相互関係を可視化")
    )

    suspend fun seed(
        entryDao: EntryDao,
        thoughtDao: EntryThoughtDao,
        definitionDao: EntryDefinitionDao,
        topicDao: TopicDao? = null,
        quizDao: QuizDao? = null,
        connectionDao: ConnectionDao? = null,
        whiteboardDao: WhiteboardDao? = null,
        wikiDao: WikiArticleDao? = null
    ) {
        // Only seed if the database is empty
        val count = entryDao.observeCount().first()
        if (count > 0) return

        val now = System.currentTimeMillis()

        topics.forEach { topicDao?.insert(it) }

        val entryIdMap = mutableMapOf<String, String>()

        definitions.forEach { def ->
            val id = UUID.randomUUID().toString()
            entryIdMap[def.term] = id
            entryDao.insert(
                EntryEntity(
                    id = id, type = "definition", title = def.term,
                    createdAt = now, updatedAt = now, accessedAt = now
                )
            )
            definitionDao.insert(
                EntryDefinitionEntity(
                    entryId = id, term = def.term, reading = def.reading,
                    definition = def.definition, field = def.field
                )
            )
        }

        thoughts.forEach { thought ->
            val id = UUID.randomUUID().toString()
            entryIdMap[thought.title] = id
            entryDao.insert(
                EntryEntity(
                    id = id, type = "thought", title = thought.title,
                    content = thought.content,
                    createdAt = now, updatedAt = now, accessedAt = now
                )
            )
            thoughtDao.insert(EntryThoughtEntity(entryId = id))
        }

        // Seed quizzes
        quizzes.forEach { q ->
            val choicesJson = "[" + q.choices.joinToString(",") { "\"$it\"" } + "]"
            quizDao?.insertQuiz(
                QuizBankEntity(
                    question = q.question,
                    answer = q.answer,
                    choicesJson = choicesJson,
                    explanation = q.explanation,
                    quizType = q.quizType,
                    generationMethod = "manual"
                )
            )
        }

        // ── 接続デモを充実: 4件のリンク ──
        suspend fun addConn(aTerm: String, bTerm: String, type: String, strength: Float, note: String) {
            val a = entryIdMap[aTerm] ?: return
            val b = entryIdMap[bTerm] ?: return
            if (connectionDao == null) return
            val ca = if (a < b) a else b
            val cb = if (a < b) b else a
            try { connectionDao.insert(ConnectionEntity(entryAId = a, entryBId = b, relationType = type, strength = strength, note = note, isAuto = false, isDirected = false, canonicalA = ca, canonicalB = cb)) } catch (_: Exception) {}
        }
        // 既存1件 + 追加3件
        addConn("明治維新", "関ヶ原の戦い", "related", 0.8f, "日本の歴史的転換点同士の接続")
        addConn("ハッシュテーブル", "再帰", "prerequisite", 0.9f, "再帰の基底ケース理解がハッシュの衝突処理に通じる")
        addConn("光合成", "需要と供給", "contrast", 0.4f, "自然の均衡と市場の均衡 — 異分野の類比")
        addConn("学習システムを7回作り直してわかったこと", "百科事典のように知識を育てたい", "related", 0.85f, "点→線→面の思想的接続")

        // ── ホワイトボードデモ: 2ボード ──
        if (whiteboardDao != null) {
            for ((boardIdx, boardDef) in demoBoards.withIndex()) {
                val boardId = UUID.randomUUID().toString()
                whiteboardDao.upsertBoard(WhiteboardEntity(id = boardId, title = boardDef.title, summary = boardDef.summary, createdAt = now, updatedAt = now))
                val sectionId = UUID.randomUUID().toString()
                whiteboardDao.upsertSection(WhiteboardSectionEntity(id = sectionId, boardId = boardId, title = if (boardIdx == 0) "江戸〜明治" else "データ構造", x = 40f, y = 40f, width = 700f, height = 420f, colorHex = if (boardIdx == 0) "#FEF3C7" else "#DBEAFE"))
                val entryTerms = if (boardIdx == 0) listOf("明治維新", "関ヶ原の戦い", "学習システムを7回作り直してわかったこと") else listOf("ハッシュテーブル", "再帰", "光合成")
                for ((idx, term) in entryTerms.withIndex()) {
                    val eId = entryIdMap[term] ?: continue
                    val node = WhiteboardNodeEntity(boardId = boardId, entryId = eId, x = 60f + idx * 220f, y = 80f, width = 200f, height = 120f, sectionId = sectionId)
                    whiteboardDao.upsertNode(node)
                }
                val notes = if (boardIdx == 0) listOf("年表: 1600関ヶ原→1868明治維新\n転換点の共通点は「既存秩序の再編」", "問い: なぜ260年続いた幕藩体制は崩れたか？\n→ 外圧と内発的矛盾") else listOf("メモ: ハッシュの衝突は再帰の基底ケースと同じく「終了条件」が肝", "図: チェイン法 vs オープンアドレス\n長所短所を比較")
                for ((idx, md) in notes.withIndex()) {
                    val noteId = UUID.randomUUID().toString()
                    whiteboardDao.upsertNote(WhiteboardNoteEntity(id = noteId, contentMd = md, createdAt = now, updatedAt = now))
                    val node = WhiteboardNodeEntity(boardId = boardId, noteId = noteId, x = 60f + idx * 260f, y = 240f, width = 240f, height = 100f, sectionId = sectionId)
                    whiteboardDao.upsertNode(node)
                }
            }
        }

        // ── Wikiデモ: 2記事 ──
        if (wikiDao != null) {
            val articles = listOf(
                WikiArticleEntity(title = "明治維新と関ヶ原 — 転換点の比較", contentMd = "# 明治維新と関ヶ原\n\n二つの転換点を [[明治維新]] と [[関ヶ原の戦い]] で比較する。\n\n- **共通点**: 既存秩序の再編、勝者の正統化\n- **相違**: 関ヶ原は武断、明治は外圧と思想\n\n> 関連: [[学習システムを7回作り直してわかったこと]] の「点→線→面」も転換点の理解に通じる。\n", summary = "歴史の転換点2件を比較したデモ記事"),
                WikiArticleEntity(title = "データ構造入門 — ハッシュと再帰", contentMd = "# データ構造入門\n\n## ハッシュテーブル\n[[ハッシュテーブル]] は平均O(1)の検索を実現する。\n\n## 再帰\n[[再帰]] の基底ケースはハッシュの衝突処理の終了条件と同型。\n\n- 図: チェイン法の連結リストは再帰的に辿る\n- 演習: [[光合成]] のような自然のネットワークも同様にグラフで表せる\n", summary = "CSの2概念を相互リンクしたデモ記事")
            )
            for (art in articles) wikiDao.upsert(art)
        }
    }
}