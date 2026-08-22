package com.thuvstu.personalencyclopedia.perf

import android.util.Log
import androidx.room.withTransaction
import com.thuvstu.personalencyclopedia.brain.ai.toBlob
import com.thuvstu.personalencyclopedia.brain.search.EmbeddingTextBuilder
import com.thuvstu.personalencyclopedia.brain.search.NgramTokenizer
import com.thuvstu.personalencyclopedia.db.AppDatabase
import com.thuvstu.personalencyclopedia.db.entity.EntryBookEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryDefinitionEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryThoughtEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryWebpageEntity
import com.thuvstu.personalencyclopedia.db.entity.EmbeddingEntity
import com.thuvstu.personalencyclopedia.db.entity.SearchDocumentEntity
import java.util.Locale
import java.util.Random
import kotlin.math.sqrt

/**
 * Round 0 (M-1): 合成負荷データ生成。
 *
 * - このファイルは debug ソースセットのみに存在するため、release APK には一切含まれない
 * - 実データ規模（数万entry）での計測用。1,000 → 10,000 → 50,000 と段階的に投入する
 * - 生成内容: entry / entry_definition / entry_thought / entry_webpage / entry_book /
 *   search_document + FTS(n-gram) / embedding(768次元の疑似ベクトル)
 *   本番の EmbeddingQueue.updateSearchDocument と同じ関数(EmbeddingTextBuilder/NgramTokenizer)を
 *   使ってテキストを作るため、起動時の rebuildAllSearchDocuments が差分なしでスキップできる
 * - 全行は metadataJson = {"synthetic":true} でマークされ、clear() で一括削除できる
 * - Random のシードは count 固定 → 同じ count なら常に同一データ（計測の再現性確保）
 */
object SyntheticDataSeeder {

    private const val TAG = "SyntheticSeeder"

    /** GeminiClient.embed の出力次元 (outputDimensionality=768) に一致させる */
    private const val EMBEDDING_DIM = 768

    /** トランザクションあたりの挿入件数 */
    private const val BATCH_SIZE = 2_000

    private const val SYNTHETIC_JSON = "{\"synthetic\":true}"

    // ── 合成テキストの語彙プール ──
    private val CATEGORIES = listOf(
        "歴史", "生物", "化学", "物理", "経済", "数学", "文学", "哲学", "地理", "医学",
        "コンピュータ", "法律", "美術", "音楽", "天文", "地質", "心理", "社会", "建築", "農業"
    )
    private val TERMS = listOf(
        "細胞分裂", "代謝経路", "量子もつれ", "遺伝子発現", "需要曲線", "確率過程", "文芸復興",
        "認知バイアス", "断層活動", "光合成", "相対性理論", "資本蓄積", "物語構造", "存在論",
        "プレート境界", "酵素反応", "時空間歪み", "独占競争", "韻律論", "現象学"
    )
    private val SENTENCES = listOf(
        "この概念は19世紀後半の研究者たちによって体系化され、その後の学問分野の発展に大きな影響を与えた。",
        "観察された事象を説明するため、複数の仮説が提唱されたが、決定的な証拠はまだ得られていない。",
        "実験結果は理論的な予測と高い整合性を示し、既存のモデルを検証する有力な根拠となった。",
        "応用面では医療・工学・情報処理など幅広い領域で活用が進んでおり、今後さらなる発展が期待される。",
        "一方で批判的な見解もあり、前提となる仮定や測定方法の妥当性について議論が続いている。",
        "関連する下位概念との関係を整理することで、全体像をより正確に把握することが可能になる。",
        "歴史的には偶然の発見から始まったが、現在では計画的な研究開発の対象として位置づけられている。",
        "統計的な傾向を見ると、サンプル母集団の特性によって結論が大きく左右される点に注意が必要である。",
        "この分野の標準的な教科書では、基礎から応用まで段階的に説明されており、入門者にも読みやすい。",
        "近年の技術進歩により、従来は不可能だった精度での測定・解析が現実のものとなりつつある。"
    )
    private val AUTHORS = listOf("山田太郎", "鈴木一郎", "佐藤花子", "Smith J.", "田中美咲", "高橋健")

    /**
     * [count]件の合成entryを段階的に投入する。
     * 既存DB（ユーザーの実データ・デモデータ）には触れず、必ず合成フラグ付きで追記する。
     *
     * 合成データが既に存在する場合は、ID衝突を避けるため**先に全削除してから**投入する
     * （= 各SEED呼び出しで合成データはちょうど[count]件になる。1,000→10,000→50,000の
     * 段階計測では、都度この関数を呼ぶだけでよい。CLEARは任意）。
     */
    suspend fun seed(db: AppDatabase, count: Int, onProgress: (Int) -> Unit = {}) {
        require(count > 0) { "count must be > 0" }
        val start = System.currentTimeMillis()
        Log.i(TAG, "seed(count=$count) 開始")

        val existing = db.entryDao().countSynthetic()
        if (existing > 0) {
            Log.i(TAG, "既存の合成データ $existing 件を削除してから投入する")
            clear(db)
        }

        val rng = java.util.Random(count.toLong())
        var inserted = 0

        while (inserted < count) {
            val n = minOf(BATCH_SIZE, count - inserted)
            insertBatch(db, rng, inserted, n)
            inserted += n
            onProgress(inserted)
            Log.i(TAG, "seed進捗: $inserted/$count")
        }

        val elapsed = System.currentTimeMillis() - start
        Log.i(TAG, "seed完了: ${count}件, ${elapsed}ms")
    }

    private suspend fun insertBatch(db: AppDatabase, rng: Random, baseIndex: Int, n: Int) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val entries = ArrayList<EntryEntity>(n)
            val definitions = ArrayList<EntryDefinitionEntity>()
            val thoughts = ArrayList<EntryThoughtEntity>()
            val webpages = ArrayList<EntryWebpageEntity>()
            val books = ArrayList<EntryBookEntity>()

            repeat(n) { i ->
                val index = baseIndex + i
                val type = pickType(rng)
                val id = String.format(Locale.US, "synthetic-%08d-%04d", baseIndex, i)
                val title = buildTitle(rng, type, index)
                val content = buildContent(rng)

                entries += EntryEntity(
                    id = id,
                    type = type,
                    title = title,
                    content = content,
                    summary = if (rng.nextInt(3) == 0) content.take(50) else null,
                    lang = "ja",
                    metadataJson = SYNTHETIC_JSON,
                    createdAt = now - randomAgeMs(rng),
                    updatedAt = now
                )

                when (type) {
                    "definition" -> definitions += EntryDefinitionEntity(
                        entryId = id,
                        term = title.substringAfterLast(" "),
                        reading = randomKatakana(rng),
                        definition = buildContent(rng),
                        field = CATEGORIES[rng.nextInt(CATEGORIES.size)]
                    )
                    "thought" -> thoughts += EntryThoughtEntity(entryId = id)
                    "webpage" -> webpages += EntryWebpageEntity(
                        entryId = id,
                        url = "https://example.com/synthetic/$index",
                        domain = "example.com",
                        author = AUTHORS[rng.nextInt(AUTHORS.size)],
                        fullText = buildContent(rng).repeat(3),
                        scrapedAt = now
                    )
                    "book" -> books += EntryBookEntity(
                        entryId = id,
                        authorsJson = "[\"${AUTHORS[rng.nextInt(AUTHORS.size)]}\"]",
                        publisher = "合成出版",
                        publishedYear = 1900 + rng.nextInt(126),
                        totalPages = 150 + rng.nextInt(600),
                        readStatus = "read"
                    )
                }
            }

            db.entryDao().insertAll(entries)
            if (definitions.isNotEmpty()) db.entryDefinitionDao().insertAll(definitions)
            if (thoughts.isNotEmpty()) db.entryThoughtDao().insertAll(thoughts)
            webpages.forEach { db.entryExtensionDao().insertWebpage(it) }
            books.forEach { db.entryExtensionDao().insertBook(it) }

            // search_document + FTS + embedding を本番パスと同じ形で作る
            seedSearchArtifacts(db, entries, definitions, webpages, books)
        }
    }

    private suspend fun seedSearchArtifacts(
        db: AppDatabase,
        entries: List<EntryEntity>,
        definitions: List<EntryDefinitionEntity>,
        webpages: List<EntryWebpageEntity>,
        books: List<EntryBookEntity>
    ) {
        val defById = definitions.associateBy { it.entryId }
        val webById = webpages.associateBy { it.entryId }
        val bookById = books.associateBy { it.entryId }
        val rng = java.util.Random(entries.firstOrNull()?.id?.hashCode()?.toLong() ?: 0L)

        val docs = entries.mapNotNull { entry ->
            val extension = when (entry.type) {
                "definition" -> defById[entry.id]
                "webpage" -> webById[entry.id]
                "book" -> bookById[entry.id]
                else -> null // thought
            }
            val combined = EmbeddingTextBuilder.build(entry, extension)
            if (combined.isBlank()) null
            else SearchDocumentEntity(
                entryId = entry.id,
                combinedText = combined,
                lang = entry.lang ?: "ja"
            )
        }
        if (docs.isEmpty()) return
        db.searchDocumentDao().insertAll(docs)

        val docsById = docs.associateBy { it.entryId }
        val rowids = db.searchDocumentDao().getRowids(docs.map { it.entryId })
        for (row in rowids) {
            val doc = docsById[row.entryId] ?: continue
            db.searchDocumentDao().insertFts(row.rowid, NgramTokenizer.tokenize(doc.combinedText))
        }

        // 疑似埋め込み（単位ベクトル）。model="synthetic-768"で本番embeddingと識別可能
        val embeddings = docs.map { doc ->
            EmbeddingEntity(
                entryId = doc.entryId,
                vectorBlob = randomUnitVector(rng).toBlob(),
                model = "synthetic-$EMBEDDING_DIM",
                inputText = doc.combinedText
            )
        }
        db.embeddingDao().insertAll(embeddings)
    }

    /** Round 0 (M-1): 合成データのみを削除する。子テーブル(embedding/search_document等)はFK CASCADEで消え、FTSのみ明示的に掃除 */
    suspend fun clear(db: AppDatabase) {
        val start = System.currentTimeMillis()
        db.withTransaction {
            db.searchDocumentDao().deleteSyntheticFts()
            db.entryDao().deleteSynthetic()
        }
        Log.i(TAG, "clear完了: ${System.currentTimeMillis() - start}ms")
    }

    // ── 生成ヘルパー ──

    private fun pickType(rng: Random): String = when (rng.nextInt(100)) {
        in 0..49 -> "definition"      // 50%
        in 50..74 -> "thought"        // 25%
        in 75..89 -> "webpage"        // 15%
        else -> "book"                // 10%
    }

    private fun buildTitle(rng: Random, type: String, index: Int): String {
        val category = CATEGORIES[rng.nextInt(CATEGORIES.size)]
        return when (type) {
            "definition" -> "${category}用語 #$index ${TERMS[rng.nextInt(TERMS.size)]}"
            "thought" -> "思考メモ #$index ${category}について考えてみたこと"
            "webpage" -> "Web記事 #$index ${category}の最新動向まとめ"
            else -> "書籍メモ #$index ${category}入門 第${1 + rng.nextInt(20)}章"
        }
    }

    private fun buildContent(rng: Random): String {
        val n = 3 + rng.nextInt(3)
        return (1..n).joinToString("") { SENTENCES[rng.nextInt(SENTENCES.size)] }
    }

    // java.util.Random.nextLong(bound) は minSdk 28 のAndroidランタイムに存在しないため自前で作る
    private fun nextLongBounded(rng: Random, bound: Long): Long =
        (rng.nextDouble() * bound).toLong().coerceIn(0, bound - 1)

    private fun randomAgeMs(rng: Random): Long = nextLongBounded(rng, 365L * 24 * 60 * 60 * 1000)

    private fun randomKatakana(rng: Random): String =
        (1..4).joinToString("") { ((0x30A2 + rng.nextInt(80)).toChar()).toString() }

    private fun randomUnitVector(rng: Random): FloatArray {
        val v = FloatArray(EMBEDDING_DIM)
        var norm = 0.0
        for (i in v.indices) {
            v[i] = rng.nextGaussian().toFloat()
            norm += v[i].toDouble() * v[i]
        }
        norm = sqrt(norm)
        if (norm > 0) for (i in v.indices) v[i] = (v[i] / norm).toFloat()
        return v
    }
}
