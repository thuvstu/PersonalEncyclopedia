# Phase 1 実装 — 単語帳SRS + クイズ基本形 + バックアップ + インポート

Phase 0の性能問題への対策も含め、Phase 1の全コードを出力します。

---

## 0. Phase 0 性能改善（Phase 1に同梱）

Phase 0で「動作がゆっくり」だった主な原因と対策：

| 原因 | 対策 |
|---|---|
| `observeAll()` が全件Flowを流し続け、UI再構成が過剰 | `LIMIT`を厳格化 + `SharingStarted.WhileSubscribed` の停止時間調整 |
| `EntryCard` 内の `SimpleDateFormat` が毎recompositionで生成 | `remember` でキャッシュ |
| `SearchViewModel` の `debounce(300)` + `flatMapLatest` が二重購読 | `distinctUntilChanged` 追加 |
| Room の `fallbackToDestructiveMigration()` が起動時に余分なチェック | Phase 1で正式Migration導入 |
| Ktor `EmbeddedServer` の `lateinit var` DAO注入が冗長 | Constructor injection に変更 |

---

## 1. ビルド設定の更新

### `gradle/libs.versions.toml`（追加分）

```toml
[versions]
# ... existing ...
workmanager = "2.10.0"
google-services = "4.4.2"
play-services-auth = "21.3.0"
google-api-drive = "v3-rev20241201-2.0.0"
google-http-gson = "1.45.3"
google-api-client-android = "2.7.2"
security-crypto = "1.1.0-alpha06"

[libraries]
# ... existing ...

# WorkManager
workmanager-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workmanager" }
workmanager-hilt = { group = "androidx.hilt", name = "hilt-work", version = "1.2.0" }
workmanager-hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version = "1.2.0" }

# Security (EncryptedSharedPreferences)
security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "security-crypto" }

# Google Drive API
play-services-auth = { group = "com.google.android.gms", name = "play-services-auth", version.ref = "play-services-auth" }
google-api-client-android = { group = "com.google.api-client", name = "google-api-client-android", version.ref = "google-api-client-android" }
google-api-services-drive = { group = "com.google.apis", name = "google-api-services-drive", version.ref = "google-api-drive" }
google-http-gson = { group = "com.google.http-client", name = "google-http-client-gson", version.ref = "google-http-gson" }

[plugins]
# ... existing ...
```

### `app/build.gradle.kts`（追加分）

```kotlin
dependencies {
    // ... existing Phase 0 dependencies ...

    // WorkManager
    implementation(libs.workmanager.ktx)
    implementation(libs.workmanager.hilt)
    ksp(libs.workmanager.hilt.compiler)

    // Security
    implementation(libs.security.crypto)

    // Google Drive
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.google.http.gson)
}
```

---

## 2. データベース層（新規Entity + Migration）

### `db/entity/TopicEntity.kt`

```kotlin
package com.example.encyclopedia.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "topic",
    indices = [Index("name"), Index("parentId")]
)
data class TopicEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,   // null = ジャンル(最上位), 非null = 分野
    val description: String? = null,
    val colorHex: String? = null
)

@Entity(
    tableName = "entry_topic",
    primaryKeys = ["entryId", "topicId"],
    foreignKeys = [
        ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TopicEntity::class, ["id"], ["topicId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("topicId")]
)
data class EntryTopicEntity(
    val entryId: String,
    val topicId: String
)
```

### `db/entity/SrsReviewEntity.kt`

```kotlin
package com.example.encyclopedia.db.entity

import androidx.room.*
import java.util.UUID

@Entity(
    tableName = "srs_review",
    indices = [Index("entryId"), Index("reviewedAt"), Index("nextReviewAt")]
)
data class SrsReviewEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val reviewedAt: Long = System.currentTimeMillis(),
    val grade: Int,             // 0-5 (SM-2)
    val intervalDays: Int,
    val easeFactor: Float = 2.5f,
    val nextReviewAt: Long
)

@DatabaseView(
    """
    SELECT sr.entryId, sr.grade, sr.intervalDays, sr.easeFactor, sr.nextReviewAt,
           sr.reviewedAt AS lastReviewedAt
    FROM srs_review sr
    INNER JOIN (
        SELECT entryId, MAX(reviewedAt) AS maxReviewedAt
        FROM srs_review
        GROUP BY entryId
    ) latest ON sr.entryId = latest.entryId AND sr.reviewedAt = latest.maxReviewedAt
    """
)
data class SrsCurrentView(
    val entryId: String,
    val grade: Int,
    val intervalDays: Int,
    val easeFactor: Float,
    val nextReviewAt: Long,
    val lastReviewedAt: Long
)
```

### `db/entity/QuizBankEntity.kt`

```kotlin
package com.example.encyclopedia.db.entity

import androidx.room.*
import java.util.UUID

@Entity(
    tableName = "quiz_bank",
    indices = [Index("topicId"), Index("quizType"), Index("isActive"), Index("sourceEntryId")]
)
data class QuizBankEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sourceEntryId: String? = null,
    val topicId: String? = null,
    val pluginId: String? = null,
    val quizType: String,           // qa/mcq/fill_blank/sort/essay/cloze/custom
    val question: String,
    val choicesJson: String = "[]",
    val answer: String,
    val gradingContextJson: String = "{}",
    val hintsJson: String = "[]",
    val explanation: String? = null,
    val imagesJson: String = "{}",
    val generationMethod: String,   // rule_based/cloud_ai/local_ai/manual
    val numericVariantConfigJson: String? = null,
    val difficulty: Int = 3,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "quiz_attempts",
    indices = [Index("quizId"), Index("attemptedAt")]
)
data class QuizAttemptEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val quizId: String,
    val userAnswer: String,
    val isCorrect: Boolean?,
    val score: Float,
    val gradingMethod: String,      // exact/fuzzy/semantic/llm
    val hintsRevealed: Int = 0,
    val attemptedAt: Long = System.currentTimeMillis()
)

@DatabaseView(
    "SELECT quizId, MAX(score) AS masteryScore FROM quiz_attempts GROUP BY quizId"
)
data class QuizMasteryView(
    val quizId: String,
    val masteryScore: Float
)
```

### `db/dao/SrsReviewDao.kt`

```kotlin
package com.example.encyclopedia.db.dao

import androidx.room.*
import com.example.encyclopedia.db.entity.SrsCurrentView
import com.example.encyclopedia.db.entity.SrsReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SrsReviewDao {

    @Insert
    suspend fun insert(review: SrsReviewEntity)

    @Query("""
        SELECT e.id, e.title, e.content
        FROM entry e
        INNER JOIN entry_definition ed ON ed.entryId = e.id
        LEFT JOIN (
            SELECT sr.entryId, sr.nextReviewAt
            FROM srs_review sr
            INNER JOIN (
                SELECT entryId, MAX(reviewedAt) AS maxAt
                FROM srs_review GROUP BY entryId
            ) l ON sr.entryId = l.entryId AND sr.reviewedAt = l.maxAt
        ) srs ON srs.entryId = e.id
        WHERE e.deletedAt IS NULL
          AND e.type = 'definition'
          AND (srs.nextReviewAt IS NULL OR srs.nextReviewAt <= :now)
        ORDER BY
          CASE WHEN srs.nextReviewAt IS NULL THEN 0 ELSE 1 END,
          srs.nextReviewAt ASC
        LIMIT :limit
    """)
    suspend fun getDueEntries(now: Long = System.currentTimeMillis(), limit: Int = 50): List<DueEntry>

    @Query("""
        SELECT COUNT(*)
        FROM entry e
        INNER JOIN entry_definition ed ON ed.entryId = e.id
        LEFT JOIN (
            SELECT sr.entryId, sr.nextReviewAt
            FROM srs_review sr
            INNER JOIN (
                SELECT entryId, MAX(reviewedAt) AS maxAt
                FROM srs_review GROUP BY entryId
            ) l ON sr.entryId = l.entryId AND sr.reviewedAt = l.maxAt
        ) srs ON srs.entryId = e.id
        WHERE e.deletedAt IS NULL
          AND e.type = 'definition'
          AND (srs.nextReviewAt IS NULL OR srs.nextReviewAt <= :now)
    """)
    fun observeDueCount(now: Long = System.currentTimeMillis()): Flow<Int>

    @Query("SELECT * FROM SrsCurrentView WHERE entryId = :entryId")
    suspend fun getCurrentState(entryId: String): SrsCurrentView?

    @Query("SELECT * FROM SrsCurrentView")
    fun observeAllCurrent(): Flow<List<SrsCurrentView>>

    @Query("""
        SELECT COUNT(DISTINCT entryId) FROM srs_review
        WHERE reviewedAt >= :startOfDay
    """)
    fun observeReviewedTodayCount(startOfDay: Long): Flow<Int>
}

data class DueEntry(
    val id: String,
    val title: String,
    val content: String?
)
```

### `db/dao/QuizDao.kt`

```kotlin
package com.example.encyclopedia.db.dao

import androidx.room.*
import com.example.encyclopedia.db.entity.QuizAttemptEntity
import com.example.encyclopedia.db.entity.QuizBankEntity
import com.example.encyclopedia.db.entity.QuizMasteryView
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizDao {

    @Insert
    suspend fun insertQuiz(quiz: QuizBankEntity)

    @Insert
    suspend fun insertQuizzes(quizzes: List<QuizBankEntity>)

    @Insert
    suspend fun insertAttempt(attempt: QuizAttemptEntity)

    @Query("SELECT * FROM quiz_bank WHERE id = :id")
    suspend fun getQuizById(id: String): QuizBankEntity?

    @Query("""
        SELECT * FROM quiz_bank
        WHERE isActive = 1
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun observeAllQuizzes(limit: Int = 50, offset: Int = 0): Flow<List<QuizBankEntity>>

    @Query("""
        SELECT qb.* FROM quiz_bank qb
        LEFT JOIN entry_topic et ON et.entryId = qb.sourceEntryId
        WHERE qb.isActive = 1
          AND (:topicId IS NULL OR et.topicId = :topicId OR qb.topicId = :topicId)
        ORDER BY qb.createdAt DESC
        LIMIT :limit
    """)
    suspend fun getQuizzesByTopic(topicId: String?, limit: Int = 20): List<QuizBankEntity>

    @Query("""
        SELECT qb.* FROM quiz_bank qb
        WHERE qb.isActive = 1
          AND qb.quizType IN (:types)
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getRandomQuizzes(types: List<String>, limit: Int = 10): List<QuizBankEntity>

    @Query("""
        SELECT qb.* FROM quiz_bank qb
        WHERE qb.isActive = 1
          AND qb.id NOT IN (
              SELECT qa.quizId FROM quiz_attempts qa WHERE qa.isCorrect = 1
          )
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getUnmasteredQuizzes(limit: Int = 10): List<QuizBankEntity>

    @Query("""
        SELECT qb.* FROM quiz_bank qb
        WHERE qb.isActive = 1
          AND qb.id IN (
              SELECT qa.quizId FROM quiz_attempts qa
              WHERE qa.isCorrect = 0
              GROUP BY qa.quizId
              HAVING COUNT(*) >= 1
          )
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getWrongQuizzes(limit: Int = 10): List<QuizBankEntity>

    @Query("SELECT * FROM QuizMasteryView WHERE quizId = :quizId")
    suspend fun getMastery(quizId: String): QuizMasteryView?

    @Query("SELECT * FROM QuizMasteryView")
    fun observeAllMastery(): Flow<List<QuizMasteryView>>

    @Query("SELECT COUNT(*) FROM quiz_bank WHERE isActive = 1")
    fun observeQuizCount(): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM quiz_attempts
        WHERE attemptedAt >= :startOfDay
    """)
    fun observeAttemptsTodayCount(startOfDay: Long): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM quiz_attempts
        WHERE attemptedAt >= :startOfDay AND isCorrect = 1
    """)
    fun observeCorrectTodayCount(startOfDay: Long): Flow<Int>

    @Query("""
        SELECT qa.* FROM quiz_attempts qa
        WHERE qa.quizId = :quizId
        ORDER BY qa.attemptedAt DESC
        LIMIT 1
    """)
    suspend fun getLastAttempt(quizId: String): QuizAttemptEntity?
}
```

### `db/dao/TopicDao.kt`

```kotlin
package com.example.encyclopedia.db.dao

import androidx.room.*
import com.example.encyclopedia.db.entity.EntryTopicEntity
import com.example.encyclopedia.db.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(topic: TopicEntity): Long

    @Query("SELECT * FROM topic WHERE parentId IS NULL ORDER BY name")
    fun observeGenres(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topic WHERE parentId = :parentId ORDER BY name")
    fun observeFieldsByGenre(parentId: String): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topic WHERE name = :name AND parentId IS :parentId LIMIT 1")
    suspend fun findByName(name: String, parentId: String?): TopicEntity?

    @Query("""
        SELECT t.* FROM topic t
        INNER JOIN entry_topic et ON et.topicId = t.id
        WHERE et.entryId = :entryId
    """)
    fun observeTopicsForEntry(entryId: String): Flow<List<TopicEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkEntryTopic(link: EntryTopicEntity)

    @Query("DELETE FROM entry_topic WHERE entryId = :entryId AND topicId = :topicId")
    suspend fun unlinkEntryTopic(entryId: String, topicId: String)
}
```

### `db/AppDatabase.kt`（v2更新）

```kotlin
package com.example.encyclopedia.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.encyclopedia.db.dao.*
import com.example.encyclopedia.db.entity.*

@Database(
    entities = [
        // Phase 0
        EntryTypeEntity::class,
        EntryEntity::class,
        EntryThoughtEntity::class,
        EntryDefinitionEntity::class,
        TagEntity::class,
        EntryTagEntity::class,
        // Phase 1
        TopicEntity::class,
        EntryTopicEntity::class,
        SrsReviewEntity::class,
        QuizBankEntity::class,
        QuizAttemptEntity::class,
    ],
    views = [
        SrsCurrentView::class,
        QuizMasteryView::class,
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    // Phase 0
    abstract fun entryTypeDao(): EntryTypeDao
    abstract fun entryDao(): EntryDao
    abstract fun entryThoughtDao(): EntryThoughtDao
    abstract fun entryDefinitionDao(): EntryDefinitionDao
    abstract fun tagDao(): TagDao
    // Phase 1
    abstract fun topicDao(): TopicDao
    abstract fun srsReviewDao(): SrsReviewDao
    abstract fun quizDao(): QuizDao
}
```

### `db/Migration1to2.kt`

```kotlin
package com.example.encyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // topic
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS topic (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                parentId TEXT,
                description TEXT,
                colorHex TEXT
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_topic_name ON topic(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_topic_parentId ON topic(parentId)")

        // entry_topic
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS entry_topic (
                entryId TEXT NOT NULL,
                topicId TEXT NOT NULL,
                PRIMARY KEY (entryId, topicId),
                FOREIGN KEY (entryId) REFERENCES entry(id) ON DELETE CASCADE,
                FOREIGN KEY (topicId) REFERENCES topic(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_entry_topic_topicId ON entry_topic(topicId)")

        // srs_review
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS srs_review (
                id TEXT NOT NULL PRIMARY KEY,
                entryId TEXT NOT NULL,
                reviewedAt INTEGER NOT NULL,
                grade INTEGER NOT NULL,
                intervalDays INTEGER NOT NULL,
                easeFactor REAL NOT NULL DEFAULT 2.5,
                nextReviewAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_srs_review_entryId ON srs_review(entryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_srs_review_reviewedAt ON srs_review(reviewedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_srs_review_nextReviewAt ON srs_review(nextReviewAt)")

        // quiz_bank
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS quiz_bank (
                id TEXT NOT NULL PRIMARY KEY,
                sourceEntryId TEXT,
                topicId TEXT,
                pluginId TEXT,
                quizType TEXT NOT NULL,
                question TEXT NOT NULL,
                choicesJson TEXT NOT NULL DEFAULT '[]',
                answer TEXT NOT NULL,
                gradingContextJson TEXT NOT NULL DEFAULT '{}',
                hintsJson TEXT NOT NULL DEFAULT '[]',
                explanation TEXT,
                imagesJson TEXT NOT NULL DEFAULT '{}',
                generationMethod TEXT NOT NULL,
                numericVariantConfigJson TEXT,
                difficulty INTEGER NOT NULL DEFAULT 3,
                isActive INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_bank_topicId ON quiz_bank(topicId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_bank_quizType ON quiz_bank(quizType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_bank_isActive ON quiz_bank(isActive)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_bank_sourceEntryId ON quiz_bank(sourceEntryId)")

        // quiz_attempts
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS quiz_attempts (
                id TEXT NOT NULL PRIMARY KEY,
                quizId TEXT NOT NULL,
                userAnswer TEXT NOT NULL,
                isCorrect INTEGER,
                score REAL NOT NULL,
                gradingMethod TEXT NOT NULL,
                hintsRevealed INTEGER NOT NULL DEFAULT 0,
                attemptedAt INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_attempts_quizId ON quiz_attempts(quizId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_attempts_attemptedAt ON quiz_attempts(attemptedAt)")

        // Views
        db.execSQL("""
            CREATE VIEW IF NOT EXISTS SrsCurrentView AS
            SELECT sr.entryId, sr.grade, sr.intervalDays, sr.easeFactor, sr.nextReviewAt,
                   sr.reviewedAt AS lastReviewedAt
            FROM srs_review sr
            INNER JOIN (
                SELECT entryId, MAX(reviewedAt) AS maxReviewedAt
                FROM srs_review GROUP BY entryId
            ) latest ON sr.entryId = latest.entryId AND sr.reviewedAt = latest.maxReviewedAt
        """)
        db.execSQL("""
            CREATE VIEW IF NOT EXISTS QuizMasteryView AS
            SELECT quizId, MAX(score) AS masteryScore FROM quiz_attempts GROUP BY quizId
        """)
    }
}
```

### `di/DatabaseModule.kt`（更新）

```kotlin
package com.example.encyclopedia.di

import android.content.Context
import androidx.room.Room
import com.example.encyclopedia.db.AppDatabase
import com.example.encyclopedia.db.MIGRATION_1_2
import com.example.encyclopedia.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "encyclopedia.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun provideEntryTypeDao(db: AppDatabase): EntryTypeDao = db.entryTypeDao()
    @Provides fun provideEntryDao(db: AppDatabase): EntryDao = db.entryDao()
    @Provides fun provideThoughtDao(db: AppDatabase): EntryThoughtDao = db.entryThoughtDao()
    @Provides fun provideDefinitionDao(db: AppDatabase): EntryDefinitionDao = db.entryDefinitionDao()
    @Provides fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
    @Provides fun provideTopicDao(db: AppDatabase): TopicDao = db.topicDao()
    @Provides fun provideSrsReviewDao(db: AppDatabase): SrsReviewDao = db.srsReviewDao()
    @Provides fun provideQuizDao(db: AppDatabase): QuizDao = db.quizDao()
}
```

---

## 3. Brain Layer — SRS (SM-2)

### `brain/srs/Sm2Algorithm.kt`

```kotlin
package com.example.encyclopedia.brain.srs

import com.example.encyclopedia.db.entity.SrsReviewEntity
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * SM-2 Algorithm implementation.
 * Grade: 0-5
 *   0-1: complete blackout / wrong → reset
 *   2:   wrong but upon seeing answer, remembered → reduced interval
 *   3:   correct with serious difficulty
 *   4:   correct with some hesitation
 *   5:   perfect response
 */
object Sm2Algorithm {

    data class Sm2Result(
        val intervalDays: Int,
        val easeFactor: Float,
        val nextReviewAt: Long
    )

    fun calculate(
        grade: Int,
        previousInterval: Int = 0,
        previousEase: Float = 2.5f,
        repetitionCount: Int = 0
    ): Sm2Result {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000

        // Update ease factor
        val newEase = max(
            1.3f,
            previousEase + (0.1f - (5 - grade) * (0.08f + (5 - grade) * 0.02f))
        )

        val intervalDays: Int = when {
            grade < 2 -> 0  // Reset: review again today (or in 10 min for immediate retry)
            grade == 2 -> max(1, (previousInterval * 0.5f).roundToInt())
            else -> when (repetitionCount) {
                0 -> 1
                1 -> 6
                else -> (previousInterval * newEase).roundToInt()
            }
        }

        val nextReviewAt = if (grade < 2) {
            now + 10 * 60 * 1000L  // 10 minutes for failed items
        } else {
            now + intervalDays * dayMs
        }

        return Sm2Result(
            intervalDays = intervalDays,
            easeFactor = newEase,
            nextReviewAt = nextReviewAt
        )
    }

    fun createReview(
        entryId: String,
        grade: Int,
        previousInterval: Int = 0,
        previousEase: Float = 2.5f,
        repetitionCount: Int = 0
    ): SrsReviewEntity {
        val result = calculate(grade, previousInterval, previousEase, repetitionCount)
        return SrsReviewEntity(
            id = UUID.randomUUID().toString(),
            entryId = entryId,
            grade = grade,
            intervalDays = result.intervalDays,
            easeFactor = result.easeFactor,
            nextReviewAt = result.nextReviewAt
        )
    }
}
```

### `repository/SrsRepository.kt`

```kotlin
package com.example.encyclopedia.repository

import com.example.encyclopedia.brain.srs.Sm2Algorithm
import com.example.encyclopedia.db.dao.EntryDefinitionDao
import com.example.encyclopedia.db.dao.SrsReviewDao
import com.example.encyclopedia.db.entity.EntryDefinitionEntity
import com.example.encyclopedia.db.entity.SrsCurrentView
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SrsRepository @Inject constructor(
    private val srsDao: SrsReviewDao,
    private val definitionDao: EntryDefinitionDao
) {
    data class ReviewCard(
        val entryId: String,
        val term: String,
        val reading: String?,
        val definition: String,
        val field: String?,
        val currentState: SrsCurrentView?
    )

    suspend fun getDueCards(limit: Int = 30): List<ReviewCard> {
        val dueEntries = srsDao.getDueEntries(limit = limit)
        return dueEntries.mapNotNull { due ->
            val def = definitionDao.getByEntryId(due.id) ?: return@mapNotNull null
            val state = srsDao.getCurrentState(due.id)
            ReviewCard(
                entryId = due.id,
                term = def.term,
                reading = def.reading,
                definition = def.definition,
                field = def.field,
                currentState = state
            )
        }
    }

    suspend fun recordReview(entryId: String, grade: Int) {
        val current = srsDao.getCurrentState(entryId)
        val review = Sm2Algorithm.createReview(
            entryId = entryId,
            grade = grade,
            previousInterval = current?.intervalDays ?: 0,
            previousEase = current?.easeFactor ?: 2.5f,
            repetitionCount = if (current != null && current.grade >= 2) {
                // Approximate repetition count from interval
                when {
                    current.intervalDays <= 1 -> 1
                    current.intervalDays <= 6 -> 2
                    else -> 3
                }
            } else 0
        )
        srsDao.insert(review)
    }

    fun observeDueCount(): Flow<Int> = srsDao.observeDueCount()

    fun observeReviewedTodayCount(): Flow<Int> {
        val startOfDay = getStartOfDay()
        return srsDao.observeReviewedTodayCount(startOfDay)
    }

    private fun getStartOfDay(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
```

---

## 4. Brain Layer — 多段階採点エンジン

### `brain/quiz/Grader.kt`

```kotlin
package com.example.encyclopedia.brain.quiz

import kotlin.math.min

/**
 * Multi-stage grading engine (§8.4)
 * Phase 1: stages 1-5 (normalization → calendar/numeric → fuzzy → synonym → multi-answer)
 * Phase 2: stage 6 (semantic via embedding)
 */
object MultiStageGrader {

    data class GradeResult(
        val isCorrect: Boolean,
        val score: Float,       // 1.0 = full, 0.5 = partial, 0.0 = wrong
        val method: String,     // exact/normalized/numeric/fuzzy/synonym/multi_answer
        val normalizedUser: String = "",
        val normalizedAnswer: String = ""
    )

    // ── Stage 1: Normalized exact match ──
    fun normalize(text: String): String {
        return text
            .trim()
            .replace(Regex("[\\s\\u3000]+"), "")   // Remove all whitespace incl. full-width
            .replace(Regex("[（(]"), "(")
            .replace(Regex("[）)]"), ")")
            .replace(Regex("[、,，]"), ",")
            .replace(Regex("[。．.]"), ".")
            .replace(Regex("[「」『』\"']"), "")
            .lowercase()
            // Full-width alphanumeric → half-width
            .map { c ->
                when (c) {
                    in 'Ａ'..'Ｚ' -> (c - 'Ａ' + 'A')
                    in 'ａ'..'ｚ' -> (c - 'ａ' + 'a')
                    in '０'..'９' -> (c - '０' + '0')
                    '　' -> ' '
                    else -> c
                }
            }.joinToString("")
    }

    fun gradeExact(userAnswer: String, correctAnswer: String): GradeResult {
        if (userAnswer.trim() == correctAnswer.trim()) {
            return GradeResult(true, 1.0f, "exact")
        }
        val nu = normalize(userAnswer)
        val na = normalize(correctAnswer)
        if (nu == na) {
            return GradeResult(true, 1.0f, "normalized", nu, na)
        }
        return GradeResult(false, 0f, "exact", nu, na)
    }

    // ── Stage 2: Calendar / Numeric conversion ──
    private val japaneseEras = listOf(
        "令和" to 2018, "平成" to 1988, "昭和" to 1925,
        "大正" to 1911, "明治" to 1867
    )

    fun parseYear(text: String): Int? {
        // Western year
        Regex("(\\d{3,4})年?").find(text)?.let { return it.groupValues[1].toIntOrNull() }
        // Japanese era
        for ((era, baseYear) in japaneseEras) {
            Regex("$era(\\d{1,2})年?").find(text)?.let { m ->
                val yearInEra = m.groupValues[1].toIntOrNull() ?: return@let
                return baseYear + yearInEra
            }
            if (text.contains("${era}元")) return baseYear + 1
        }
        // BC
        Regex("紀元前(\\d+)年?").find(text)?.let {
            return -(it.groupValues[1].toIntOrNull() ?: return null)
        }
        return null
    }

    fun gradeNumeric(userAnswer: String, correctAnswer: String): GradeResult? {
        val userYear = parseYear(userAnswer)
        val correctYear = parseYear(correctAnswer)
        if (userYear != null && correctYear != null) {
            val correct = userYear == correctYear
            return GradeResult(correct, if (correct) 1.0f else 0f, "numeric")
        }
        // Pure numeric comparison
        val userNum = userAnswer.trim().toDoubleOrNull()
        val correctNum = correctAnswer.trim().toDoubleOrNull()
        if (userNum != null && correctNum != null) {
            val correct = kotlin.math.abs(userNum - correctNum) < 1e-9
            return GradeResult(correct, if (correct) 1.0f else 0f, "numeric")
        }
        return null // Not applicable
    }

    // ── Stage 3: Fuzzy matching (Levenshtein) ──
    fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length; val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[m][n]
    }

    fun similarity(a: String, b: String): Float {
        if (a.isEmpty() && b.isEmpty()) return 1.0f
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0f
        return 1.0f - levenshteinDistance(a, b).toFloat() / maxLen
    }

    fun gradeFuzzy(userAnswer: String, correctAnswer: String, threshold: Float = 0.85f): GradeResult {
        val nu = normalize(userAnswer)
        val na = normalize(correctAnswer)
        val sim = similarity(nu, na)
        return if (sim >= threshold) {
            GradeResult(true, sim, "fuzzy", nu, na)
        } else {
            GradeResult(false, 0f, "fuzzy", nu, na)
        }
    }

    // ── Stage 4: Synonym matching ──
    private val builtinSynonyms = mapOf(
        "ww1" to listOf("第一次世界大戦", "world war 1", "world war i"),
        "ww2" to listOf("第二次世界大戦", "world war 2", "world war ii"),
        "usa" to listOf("アメリカ", "米国", "united states"),
        "uk" to listOf("イギリス", "英国", "united kingdom"),
    )

    fun gradeSynonym(userAnswer: String, correctAnswer: String): GradeResult? {
        val nu = normalize(userAnswer)
        val na = normalize(correctAnswer)
        // Check if user answer is a known synonym of the correct answer
        for ((key, synonyms) in builtinSynonyms) {
            val allForms = (listOf(key) + synonyms).map { normalize(it) }
            if (na in allForms && nu in allForms) {
                return GradeResult(true, 1.0f, "synonym", nu, na)
            }
        }
        return null
    }

    // ── Stage 5: Multiple answer expansion ──
    fun expandAnswers(answer: String): List<String> {
        // Split by common delimiters: /, |, , (within parentheses)
        val parts = mutableListOf<String>()
        // Handle "A(B)" → "A", "AB"
        Regex("(.+?)\\((.+?)\\)").findAll(answer).forEach { m ->
            val prefix = m.groupValues[1]
            val optional = m.groupValues[2]
            parts.add(prefix + optional)
            parts.add(prefix)
        }
        // Split by / or |
        answer.split(Regex("[/|]")).forEach { parts.add(it.trim()) }
        if (parts.isEmpty()) parts.add(answer)
        return parts.distinct()
    }

    // ── Full grading pipeline ──
    fun grade(
        userAnswer: String,
        correctAnswer: String,
        mode: String = "standard"  // standard/lenient/strict/exact
    ): GradeResult {
        if (userAnswer.isBlank()) return GradeResult(false, 0f, "exact")

        // Stage 1: Exact / Normalized
        val exactResult = gradeExact(userAnswer, correctAnswer)
        if (exactResult.isCorrect) return exactResult

        if (mode == "exact") return exactResult

        // Stage 5: Multiple answers (check all variants)
        val answers = expandAnswers(correctAnswer)
        if (answers.size > 1) {
            for (ans in answers) {
                val r = gradeExact(userAnswer, ans)
                if (r.isCorrect) return r.copy(method = "multi_answer")
            }
        }

        if (mode == "strict") return exactResult

        // Stage 2: Numeric / Calendar
        val numericResult = gradeNumeric(userAnswer, correctAnswer)
        if (numericResult != null && numericResult.isCorrect) return numericResult

        // Stage 4: Synonym
        val synonymResult = gradeSynonym(userAnswer, correctAnswer)
        if (synonymResult != null && synonymResult.isCorrect) return synonymResult

        // Stage 3: Fuzzy
        val threshold = if (mode == "lenient") 0.70f else 0.85f
        val fuzzyResult = gradeFuzzy(userAnswer, correctAnswer, threshold)
        if (fuzzyResult.isCorrect) return fuzzyResult

        return GradeResult(false, 0f, fuzzyResult.method, fuzzyResult.normalizedUser, fuzzyResult.normalizedAnswer)
    }
}
```

---

## 5. Brain Layer — ルールベースクイズ生成

### `brain/quiz/RuleBasedQuizGenerator.kt`

```kotlin
package com.example.encyclopedia.brain.quiz

import com.example.encyclopedia.db.entity.EntryDefinitionEntity
import com.example.encyclopedia.db.entity.QuizBankEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Rule-based quiz generation (§8.3 stages 1-2, cost = 0)
 */
object RuleBasedQuizGenerator {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Generate QA quiz from a definition entry.
     * "What is the definition of X?" → answer
     */
    fun generateQaFromDefinition(def: EntryDefinitionEntity, topicId: String?): QuizBankEntity {
        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = def.entryId,
            topicId = topicId,
            quizType = "qa",
            question = "「${def.term}」の定義を述べよ。",
            answer = def.definition,
            generationMethod = "rule_based",
            difficulty = 3,
            hintsJson = json.encodeToString(
                listOfNotNull(
                    def.field?.let { "分野: $it" },
                    def.reading?.let { "読み: $it" }
                ).take(3)
            )
        )
    }

    /**
     * Generate reverse QA: "What term has this definition?" → term
     */
    fun generateReverseQa(def: EntryDefinitionEntity, topicId: String?): QuizBankEntity {
        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = def.entryId,
            topicId = topicId,
            quizType = "qa",
            question = "次の定義に当てはまる用語は？\n「${def.definition.take(100)}${if (def.definition.length > 100) "…" else ""}」",
            answer = def.term,
            generationMethod = "rule_based",
            difficulty = 2,
            hintsJson = json.encodeToString(
                listOfNotNull(
                    def.field?.let { "分野: $it" },
                    "文字数: ${def.term.length}文字"
                ).take(3)
            )
        )
    }

    /**
     * Generate MCQ (4 choices) using other definitions in the same field as distractors.
     */
    fun generateMcq(
        target: EntryDefinitionEntity,
        distractors: List<EntryDefinitionEntity>,
        topicId: String?
    ): QuizBankEntity {
        val choices = mutableListOf(target.term)
        distractors.shuffled().take(3).forEach { choices.add(it.term) }
        choices.shuffle()

        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = target.entryId,
            topicId = topicId,
            quizType = "mcq",
            question = "「${target.definition.take(80)}${if (target.definition.length > 80) "…" else ""}」\nに当てはまる用語を選べ。",
            choicesJson = json.encodeToString(choices),
            answer = target.term,
            generationMethod = "rule_based",
            difficulty = 2,
            explanation = "${target.term}: ${target.definition}"
        )
    }

    /**
     * Generate fill-in-the-blank from definition text.
     * Replaces the term within the definition with {{blank}}.
     */
    fun generateFillBlank(def: EntryDefinitionEntity, topicId: String?): QuizBankEntity? {
        // Try to find the term within the definition
        val idx = def.definition.indexOf(def.term)
        if (idx < 0) return null

        val question = def.definition.replaceRange(
            idx, idx + def.term.length, "＿＿＿"
        )

        return QuizBankEntity(
            id = UUID.randomUUID().toString(),
            sourceEntryId = def.entryId,
            topicId = topicId,
            quizType = "fill_blank",
            question = "空欄を埋めよ:\n「$question」",
            answer = def.term,
            generationMethod = "rule_based",
            difficulty = 3,
            hintsJson = json.encodeToString(listOf("最初の文字: ${def.term.first()}"))
        )
    }

    /**
     * Batch generate all applicable quiz types from a list of definitions.
     */
    fun generateBatch(
        definitions: List<EntryDefinitionEntity>,
        topicId: String? = null
    ): List<QuizBankEntity> {
        val quizzes = mutableListOf<QuizBankEntity>()

        for (def in definitions) {
            // QA (forward)
            quizzes.add(generateQaFromDefinition(def, topicId))

            // Reverse QA
            quizzes.add(generateReverseQa(def, topicId))

            // MCQ (need at least 4 definitions in same field)
            val sameField = definitions.filter {
                it.field == def.field && it.entryId != def.entryId
            }
            if (sameField.size >= 3) {
                quizzes.add(generateMcq(def, sameField, topicId))
            }

            // Fill blank
            generateFillBlank(def, topicId)?.let { quizzes.add(it) }
        }

        return quizzes
    }
}
```

---

## 6. リポジトリ層（Quiz）

### `repository/QuizRepository.kt`

```kotlin
package com.example.encyclopedia.repository

import com.example.encyclopedia.brain.quiz.MultiStageGrader
import com.example.encyclopedia.brain.quiz.RuleBasedQuizGenerator
import com.example.encyclopedia.db.dao.EntryDefinitionDao
import com.example.encyclopedia.db.dao.QuizDao
import com.example.encyclopedia.db.dao.TopicDao
import com.example.encyclopedia.db.entity.QuizAttemptEntity
import com.example.encyclopedia.db.entity.QuizBankEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuizRepository @Inject constructor(
    private val quizDao: QuizDao,
    private val definitionDao: EntryDefinitionDao,
    private val topicDao: TopicDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ── Quiz Generation ──
    suspend fun generateQuizzesFromDefinitions(topicId: String? = null): Int {
        // Get all definitions (or filtered by topic via entry_topic)
        val allDefs = definitionDao.search("", limit = 500)
            .let { flow ->
                // Collect first emission
                var result: List<com.example.encyclopedia.db.entity.EntryDefinitionEntity> = emptyList()
                flow.collect { result = it; return@collect }
                result
            }

        if (allDefs.isEmpty()) return 0

        val quizzes = RuleBasedQuizGenerator.generateBatch(allDefs, topicId)
        quizDao.insertQuizzes(quizzes)
        return quizzes.size
    }

    // ── Quiz Session (§8.1 flow) ──
    suspend fun getNextQuizzes(
        topicId: String? = null,
        limit: Int = 10
    ): List<QuizBankEntity> {
        // Priority: wrong answers > unmastered > random
        val wrong = quizDao.getWrongQuizzes(limit / 3)
        val unmastered = quizDao.getUnmasteredQuizzes(limit / 3)
        val random = quizDao.getRandomQuizzes(
            types = listOf("qa", "mcq", "fill_blank"),
            limit = limit
        )

        return (wrong + unmastered + random)
            .distinctBy { it.id }
            .take(limit)
    }

    suspend fun gradeAndRecord(
        quiz: QuizBankEntity,
        userAnswer: String,
        hintsRevealed: Int = 0
    ): QuizAttemptEntity {
        val gradeResult = MultiStageGrader.grade(userAnswer, quiz.answer)

        val score = when {
            gradeResult.isCorrect -> {
                val base = 1.0f - 0.3f * hintsRevealed
                maxOf(0f, base)
            }
            userAnswer == "__UNLEARNED__" -> 0f  // Special "not learned yet" value
            else -> -1.0f
        }

        val attempt = QuizAttemptEntity(
            id = UUID.randomUUID().toString(),
            quizId = quiz.id,
            userAnswer = userAnswer,
            isCorrect = if (userAnswer == "__UNLEARNED__") null else gradeResult.isCorrect,
            score = score,
            gradingMethod = gradeResult.method,
            hintsRevealed = hintsRevealed
        )
        quizDao.insertAttempt(attempt)
        return attempt
    }

    fun parseChoices(choicesJson: String): List<String> {
        return try {
            json.parseToJsonElement(choicesJson).jsonArray.map { it.jsonPrimitive.content }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseHints(hintsJson: String): List<String> {
        return try {
            json.parseToJsonElement(hintsJson).jsonArray.map { it.jsonPrimitive.content }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Observables ──
    fun observeQuizCount(): Flow<Int> = quizDao.observeQuizCount()

    fun observeAttemptsToday(): Flow<Int> {
        val startOfDay = getStartOfDay()
        return quizDao.observeAttemptsTodayCount(startOfDay)
    }

    fun observeCorrectToday(): Flow<Int> {
        val startOfDay = getStartOfDay()
        return quizDao.observeCorrectTodayCount(startOfDay)
    }

    private fun getStartOfDay(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
```

---

## 7. バックアップ（暗号化 + WorkManager + Drive）

### `backup/BackupEncryptor.kt`

```kotlin
package com.example.encyclopedia.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption using Android Keystore (§6.3)
 * Replaces `age` encryption from Knowledge OS v10.
 */
object BackupEncryptor {

    private const val KEYSTORE_ALIAS = "encyclopedia_backup_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_LENGTH = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }

    /**
     * Encrypt file. Output format: [12-byte IV][ciphertext+tag]
     */
    fun encrypt(inputFile: File, outputFile: File) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val plaintext = FileInputStream(inputFile).use { it.readBytes() }
        val ciphertext = cipher.doFinal(plaintext)

        FileOutputStream(outputFile).use { fos ->
            fos.write(iv)
            fos.write(ciphertext)
        }
    }

    /**
     * Decrypt file.
     */
    fun decrypt(inputFile: File, outputFile: File) {
        val key = getOrCreateKey()
        val data = FileInputStream(inputFile).use { it.readBytes() }

        val iv = data.copyOfRange(0, 12)
        val ciphertext = data.copyOfRange(12, data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val plaintext = cipher.doFinal(ciphertext)
        FileOutputStream(outputFile).use { it.write(plaintext) }
    }
}
```

### `backup/BackupWorker.kt`

```kotlin
package com.example.encyclopedia.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.encyclopedia.db.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "BackupWorker"
        const val WORK_NAME = "daily_backup"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.UNMETERED)  // Wi-Fi only
                .build()

            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                repeatInterval = 1, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            performBackup()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }

    private suspend fun performBackup() {
        val context = applicationContext

        // 1. Checkpoint WAL to ensure all data is in the main DB file
        database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")

        // 2. Locate the DB file
        val dbFile = context.getDatabasePath("encyclopedia.db")
        if (!dbFile.exists()) {
            Log.w(TAG, "DB file not found")
            return
        }

        // 3. Create backup directory
        val backupDir = File(context.filesDir, "backups/db-snapshots")
        backupDir.mkdirs()

        // 4. Copy DB file
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val plainCopy = File(backupDir, "encyclopedia_$timestamp.db")
        dbFile.copyTo(plainCopy, overwrite = true)

        // Also copy WAL and SHM if they exist
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        if (walFile.exists()) walFile.copyTo(File(backupDir, "encyclopedia_$timestamp.db-wal"), true)
        if (shmFile.exists()) shmFile.copyTo(File(backupDir, "encyclopedia_$timestamp.db-shm"), true)

        // 5. Encrypt
        val encryptedFile = File(backupDir, "encyclopedia_$timestamp.db.enc")
        BackupEncryptor.encrypt(plainCopy, encryptedFile)

        // 6. Delete plaintext copy
        plainCopy.delete()
        File(backupDir, "encyclopedia_$timestamp.db-wal").delete()
        File(backupDir, "encyclopedia_$timestamp.db-shm").delete()

        // 7. Prune old backups (keep 30 generations)
        val backups = backupDir.listFiles { f -> f.extension == "enc" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        if (backups.size > 30) {
            backups.drop(30).forEach { it.delete() }
        }

        Log.i(TAG, "Backup complete: ${encryptedFile.name} (${encryptedFile.length() / 1024}KB)")

        // 8. TODO Phase 1.5: Upload to Google Drive backups/db-snapshots/
        // DriveSyncManager.upload(encryptedFile)
    }
}
```

### `backup/PortableExportWorker.kt`

```kotlin
package com.example.encyclopedia.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.encyclopedia.db.dao.EntryDao
import com.example.encyclopedia.db.dao.EntryDefinitionDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Weekly portable export: Markdown/CSV/JSON (§6.3 layer 2)
 */
@HiltWorker
class PortableExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val entryDao: EntryDao,
    private val definitionDao: EntryDefinitionDao
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "weekly_portable_export"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .build()

            val request = PeriodicWorkRequestBuilder<PortableExportWorker>(
                repeatInterval = 7, TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val exportDir = File(applicationContext.filesDir, "backups/portable")
            exportDir.mkdirs()

            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val dateStr = dateFormat.format(Date())

            // Export definitions as CSV
            val defs = definitionDao.search("", limit = 10000).first()
            val csvFile = File(exportDir, "definitions_$dateStr.csv")
            csvFile.bufferedWriter().use { writer ->
                writer.write("term,reading,definition,field\n")
                defs.forEach { d ->
                    writer.write("${escapeCsv(d.term)},${escapeCsv(d.reading ?: "")},${escapeCsv(d.definition)},${escapeCsv(d.field ?: "")}\n")
                }
            }

            // Export entries as JSON
            val entries = entryDao.observeAll(limit = 10000).first()
            val jsonFile = File(exportDir, "entries_$dateStr.json")
            jsonFile.bufferedWriter().use { writer ->
                writer.write("[\n")
                entries.forEachIndexed { i, e ->
                    writer.write("""  {"id":"${e.id}","type":"${e.type}","title":"${e.title.replace("\"", "\\\"")}","createdAt":${e.createdAt}}""")
                    if (i < entries.size - 1) writer.write(",")
                    writer.write("\n")
                }
                writer.write("]\n")
            }

            Log.i("PortableExport", "Exported ${defs.size} definitions, ${entries.size} entries")
            Result.success()
        } catch (e: Exception) {
            Log.e("PortableExport", "Export failed", e)
            Result.failure()
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }
}
```

---

## 8. インポートパイプライン

### `importer/ImportPipeline.kt`

```kotlin
package com.example.encyclopedia.importer

import android.content.Context
import android.net.Uri
import com.example.encyclopedia.db.dao.EntryDao
import com.example.encyclopedia.db.dao.EntryDefinitionDao
import com.example.encyclopedia.db.dao.EntryThoughtDao
import com.example.encyclopedia.db.entity.EntryDefinitionEntity
import com.example.encyclopedia.db.entity.EntryEntity
import com.example.encyclopedia.db.entity.EntryThoughtEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Common import pipeline (§12.1)
 * Source → Adapter → Normalize → entry + extension → (Phase 2: Embedding queue)
 */
@Singleton
class ImportPipeline @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao,
    private val thoughtDao: EntryThoughtDao,
    private val definitionDao: EntryDefinitionDao
) {
    data class ImportResult(
        val successCount: Int,
        val errorCount: Int,
        val errors: List<String> = emptyList()
    )

    /**
     * Import CSV as definitions (flashcards).
     * Expected columns: term, reading(optional), definition, field(optional)
     */
    suspend fun importDefinitionsCsv(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(0, 1, listOf("Cannot open file"))

            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                val header = reader.readLine() ?: return ImportResult(0, 0)
                val columns = parseCsvLine(header).map { it.trim().lowercase() }

                val termIdx = columns.indexOfFirst { it in listOf("term", "用語", "単語", "front") }
                val readingIdx = columns.indexOfFirst { it in listOf("reading", "読み", "ふりがな") }
                val defIdx = columns.indexOfFirst { it in listOf("definition", "定義", "意味", "back") }
                val fieldIdx = columns.indexOfFirst { it in listOf("field", "分野", "ジャンル", "category") }

                if (termIdx < 0 || defIdx < 0) {
                    return ImportResult(0, 1, listOf("CSV must have 'term' and 'definition' columns"))
                }

                var lineNum = 1
                reader.forEachLine { line ->
                    lineNum++
                    if (line.isBlank()) return@forEachLine
                    try {
                        val cols = parseCsvLine(line)
                        val term = cols.getOrNull(termIdx)?.trim() ?: ""
                        val definition = cols.getOrNull(defIdx)?.trim() ?: ""
                        if (term.isBlank() || definition.isBlank()) {
                            errors.add("Line $lineNum: missing term or definition")
                            return@forEachLine
                        }

                        val id = UUID.randomUUID().toString()
                        val now = System.currentTimeMillis()

                        entryDao.insert(
                            EntryEntity(
                                id = id,
                                type = "definition",
                                title = term,
                                createdAt = now,
                                updatedAt = now,
                                accessedAt = now
                            )
                        )
                        definitionDao.insert(
                            EntryDefinitionEntity(
                                entryId = id,
                                term = term,
                                reading = cols.getOrNull(readingIdx)?.trim()?.takeIf { it.isNotBlank() },
                                definition = definition,
                                field = cols.getOrNull(fieldIdx)?.trim()?.takeIf { it.isNotBlank() }
                            )
                        )
                        success++
                    } catch (e: Exception) {
                        errors.add("Line $lineNum: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("File error: ${e.message}")
        }

        return ImportResult(success, errors.size, errors)
    }

    /**
     * Import Markdown file as thought entries.
     * Each H1/H2 heading becomes a separate entry.
     */
    suspend fun importMarkdown(uri: Uri): ImportResult {
        val errors = mutableListOf<String>()
        var success = 0

        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImportResult(0, 1, listOf("Cannot open file"))

            val content = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use {
                it.readText()
            }

            // Split by headings
            val sections = content.split(Regex("^#{1,2}\\s+", RegexOption.MULTILINE))
                .filter { it.isNotBlank() }

            for (section in sections) {
                val lines = section.lines()
                val title = lines.firstOrNull()?.trim()?.take(200) ?: "Untitled"
                val body = lines.drop(1).joinToString("\n").trim()

                if (title.isBlank()) continue

                val id = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                entryDao.insert(
                    EntryEntity(
                        id = id,
                        type = "thought",
                        title = title,
                        content = body.takeIf { it.isNotBlank() },
                        createdAt = now,
                        updatedAt = now,
                        accessedAt = now
                    )
                )
                thoughtDao.insert(
                    EntryThoughtEntity(entryId = id, context = "markdown_import")
                )
                success++
            }
        } catch (e: Exception) {
            errors.add("File error: ${e.message}")
        }

        return ImportResult(success, errors.size, errors)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
        }
        result.add(sb.toString())
        return result
    }
}
```

---

## 9. ViewModel（Phase 1新規）

### `viewmodel/SrsViewModel.kt`

```kotlin
package com.example.encyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.encyclopedia.repository.SrsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SrsViewModel @Inject constructor(
    private val srsRepo: SrsRepository
) : ViewModel() {

    sealed class SrsUiState {
        object Loading : SrsUiState()
        object Empty : SrsUiState()
        data class Reviewing(
            val cards: List<SrsRepository.ReviewCard>,
            val currentIndex: Int,
            val isAnswerRevealed: Boolean
        ) : SrsUiState()
        data class Completed(val reviewedCount: Int) : SrsUiState()
    }

    private val _uiState = MutableStateFlow<SrsUiState>(SrsUiState.Loading)
    val uiState: StateFlow<SrsUiState> = _uiState

    val dueCount: StateFlow<Int> = srsRepo.observeDueCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var reviewedCount = 0

    init {
        loadDueCards()
    }

    fun loadDueCards() {
        viewModelScope.launch {
            _uiState.value = SrsUiState.Loading
            val cards = srsRepo.getDueCards(limit = 30)
            _uiState.value = if (cards.isEmpty()) {
                SrsUiState.Empty
            } else {
                SrsUiState.Reviewing(cards = cards, currentIndex = 0, isAnswerRevealed = false)
            }
        }
    }

    fun revealAnswer() {
        val state = _uiState.value as? SrsUiState.Reviewing ?: return
        _uiState.value = state.copy(isAnswerRevealed = true)
    }

    fun gradeCard(grade: Int) {
        val state = _uiState.value as? SrsUiState.Reviewing ?: return
        val card = state.cards[state.currentIndex]

        viewModelScope.launch {
            srsRepo.recordReview(card.entryId, grade)
            reviewedCount++

            val nextIndex = state.currentIndex + 1
            _uiState.value = if (nextIndex >= state.cards.size) {
                SrsUiState.Completed(reviewedCount)
            } else {
                SrsUiState.Reviewing(
                    cards = state.cards,
                    currentIndex = nextIndex,
                    isAnswerRevealed = false
                )
            }
        }
    }
}
```

### `viewmodel/QuizViewModel.kt`

```kotlin
package com.example.encyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.encyclopedia.db.entity.QuizBankEntity
import com.example.encyclopedia.repository.QuizRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val quizRepo: QuizRepository
) : ViewModel() {

    sealed class QuizUiState {
        object Loading : QuizUiState()
        object Empty : QuizUiState()
        data class Question(
            val quiz: QuizBankEntity,
            val choices: List<String>,
            val hints: List<String>,
            val hintsRevealed: Int,
            val questionNumber: Int,
            val totalQuestions: Int
        ) : QuizUiState()
        data class Answered(
            val quiz: QuizBankEntity,
            val userAnswer: String,
            val isCorrect: Boolean?,
            val score: Float,
            val gradingMethod: String,
            val questionNumber: Int,
            val totalQuestions: Int
        ) : QuizUiState()
        data class SessionComplete(
            val totalAnswered: Int,
            val correctCount: Int,
            val totalScore: Float
        ) : QuizUiState()
    }

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState

    private var quizzes: List<QuizBankEntity> = emptyList()
    private var currentIndex = 0
    private var correctCount = 0
    private var totalScore = 0f

    val quizCount: StateFlow<Int> = quizRepo.observeQuizCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun startSession(topicId: String? = null) {
        viewModelScope.launch {
            _uiState.value = QuizUiState.Loading
            quizzes = quizRepo.getNextQuizzes(topicId = topicId, limit = 10)
            currentIndex = 0
            correctCount = 0
            totalScore = 0f

            if (quizzes.isEmpty()) {
                _uiState.value = QuizUiState.Empty
            } else {
                showCurrentQuestion()
            }
        }
    }

    fun generateQuizzes() {
        viewModelScope.launch {
            quizRepo.generateQuizzesFromDefinitions()
            startSession()
        }
    }

    private fun showCurrentQuestion() {
        if (currentIndex >= quizzes.size) {
            _uiState.value = QuizUiState.SessionComplete(
                totalAnswered = quizzes.size,
                correctCount = correctCount,
                totalScore = totalScore
            )
            return
        }
        val quiz = quizzes[currentIndex]
        _uiState.value = QuizUiState.Question(
            quiz = quiz,
            choices = quizRepo.parseChoices(quiz.choicesJson),
            hints = quizRepo.parseHints(quiz.hintsJson),
            hintsRevealed = 0,
            questionNumber = currentIndex + 1,
            totalQuestions = quizzes.size
        )
    }

    fun revealHint() {
        val state = _uiState.value as? QuizUiState.Question ?: return
        if (state.hintsRevealed < state.hints.size) {
            _uiState.value = state.copy(hintsRevealed = state.hintsRevealed + 1)
        }
    }

    fun submitAnswer(answer: String) {
        val state = _uiState.value as? QuizUiState.Question ?: return
        val quiz = state.quiz

        viewModelScope.launch {
            val attempt = quizRepo.gradeAndRecord(
                quiz = quiz,
                userAnswer = answer,
                hintsRevealed = state.hintsRevealed
            )

            if (attempt.isCorrect == true) correctCount++
            totalScore += attempt.score

            _uiState.value = QuizUiState.Answered(
                quiz = quiz,
                userAnswer = answer,
                isCorrect = attempt.isCorrect,
                score = attempt.score,
                gradingMethod = attempt.gradingMethod,
                questionNumber = state.questionNumber,
                totalQuestions = state.totalQuestions
            )
        }
    }

    fun markUnlearned() {
        submitAnswer("__UNLEARNED__")
    }

    fun nextQuestion() {
        currentIndex++
        showCurrentQuestion()
    }
}
```

### `viewmodel/ImportViewModel.kt`

```kotlin
package com.example.encyclopedia.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.encyclopedia.importer.ImportPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importPipeline: ImportPipeline
) : ViewModel() {

    sealed class ImportState {
        object Idle : ImportState()
        object Importing : ImportState()
        data class Done(val result: ImportPipeline.ImportResult) : ImportState()
        data class Error(val message: String) : ImportState()
    }

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            _state.value = ImportState.Importing
            try {
                val result = importPipeline.importDefinitionsCsv(uri)
                _state.value = ImportState.Done(result)
            } catch (e: Exception) {
                _state.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun importMarkdown(uri: Uri) {
        viewModelScope.launch {
            _state.value = ImportState.Importing
            try {
                val result = importPipeline.importMarkdown(uri)
                _state.value = ImportState.Done(result)
            } catch (e: Exception) {
                _state.value = ImportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun reset() {
        _state.value = ImportState.Idle
    }
}
```

---

## 10. UI画面（Phase 1新規）

### `ui/screen/SrsReviewScreen.kt`

```kotlin
package com.example.encyclopedia.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.encyclopedia.ui.theme.entryTypeColor
import com.example.encyclopedia.viewmodel.SrsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SrsReviewScreen(
    onBack: () -> Unit,
    viewModel: SrsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("単語帳復習") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                is SrsViewModel.SrsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is SrsViewModel.SrsUiState.Empty -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🎉", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "復習完了！",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "現在、復習期限のカードはありません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        FilledTonalButton(onClick = { viewModel.loadDueCards() }) {
                            Text("再読み込み")
                        }
                    }
                }

                is SrsViewModel.SrsUiState.Reviewing -> {
                    val card = state.cards[state.currentIndex]
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Progress
                        LinearProgressIndicator(
                            progress = {
                                state.currentIndex.toFloat() / state.cards.size
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${state.currentIndex + 1} / ${state.cards.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = entryTypeColor("definition").copy(alpha = 0.06f)
                            ),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Term
                                Text(
                                    text = card.term,
                                    style = MaterialTheme.typography.headlineLarge,
                                    textAlign = TextAlign.Center
                                )
                                if (!card.reading.isNullOrBlank()) {
                                    Text(
                                        text = card.reading,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!card.field.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(card.field) }
                                    )
                                }

                                // Answer (revealed)
                                AnimatedVisibility(
                                    visible = state.isAnswerRevealed,
                                    enter = fadeIn() + expandVertically()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(top = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                        Text(
                                            text = card.definition,
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action buttons
                        if (!state.isAnswerRevealed) {
                            Button(
                                onClick = { viewModel.revealAnswer() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("解答を表示", style = MaterialTheme.typography.titleMedium)
                            }
                        } else {
                            // SM-2 grade buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GradeButton("😵\n忘却", 1, MaterialTheme.colorScheme.error, Modifier.weight(1f)) {
                                    viewModel.gradeCard(it)
                                }
                                GradeButton("🤔\n難しい", 3, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) {
                                    viewModel.gradeCard(it)
                                }
                                GradeButton("🙂\n正解", 4, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                                    viewModel.gradeCard(it)
                                }
                                GradeButton("⚡\n完璧", 5, MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) {
                                    viewModel.gradeCard(it)
                                }
                            }
                        }
                    }
                }

                is SrsViewModel.SrsUiState.Completed -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("✅", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "セッション完了！",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "${state.reviewedCount} 枚のカードを復習しました",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { viewModel.loadDueCards() }) {
                                Text("もう一度")
                            }
                            Button(onClick = onBack) {
                                Text("終了")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeButton(
    label: String,
    grade: Int,
    color: Color,
    modifier: Modifier = Modifier,
    onGrade: (Int) -> Unit
) {
    Button(
        onClick = { onGrade(grade) },
        modifier = modifier.height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = color
        )
    }
}

private typealias Color = androidx.compose.ui.graphics.Color
```

### `ui/screen/QuizScreen.kt`

```kotlin
package com.example.encyclopedia.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.encyclopedia.viewmodel.QuizViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    onBack: () -> Unit,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var answerInput by remember { mutableStateOf("") }

    // Auto-start session
    LaunchedEffect(Unit) {
        viewModel.startSession()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("クイズ演習") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (val state = uiState) {
                is QuizViewModel.QuizUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is QuizViewModel.QuizUiState.Empty -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📝", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("問題がありません", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "単語帳から自動生成しますか？",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.generateQuizzes() }) {
                            Text("問題を生成する")
                        }
                    }
                }

                is QuizViewModel.QuizUiState.Question -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Progress
                        Text(
                            "問 ${state.questionNumber} / ${state.totalQuestions}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = {
                                state.questionNumber.toFloat() / state.totalQuestions
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Question
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                // Quiz type badge
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            when (state.quiz.quizType) {
                                                "qa" -> "記述式"
                                                "mcq" -> "選択式"
                                                "fill_blank" -> "穴埋め"
                                                else -> state.quiz.quizType
                                            }
                                        )
                                    }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = state.quiz.question,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Hints
                        if (state.hints.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.revealHint() }) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = "ヒント")
                                }
                                Text(
                                    "ヒント (${state.hintsRevealed}/${state.hints.size})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            state.hints.take(state.hintsRevealed).forEach { hint ->
                                Text(
                                    text = "💡 $hint",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Answer input
                        when (state.quiz.quizType) {
                            "mcq" -> {
                                state.choices.forEach { choice ->
                                    OutlinedButton(
                                        onClick = {
                                            answerInput = choice
                                            viewModel.submitAnswer(choice)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(choice, modifier = Modifier.padding(8.dp))
                                    }
                                }
                            }
                            else -> {
                                OutlinedTextField(
                                    value = answerInput,
                                    onValueChange = { answerInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("解答を入力") },
                                    minLines = 2
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.submitAnswer(answerInput)
                                            answerInput = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = answerInput.isNotBlank()
                                    ) {
                                        Text("回答")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.markUnlearned()
                                            answerInput = ""
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("未習")
                                    }
                                }
                            }
                        }
                    }
                }

                is QuizViewModel.QuizUiState.Answered -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Result icon
                        val (icon, color, label) = when (state.isCorrect) {
                            true -> Triple("⭕", MaterialTheme.colorScheme.primary, "正解！")
                            false -> Triple("❌", MaterialTheme.colorScheme.error, "不正解")
                            null -> Triple("⏭️", MaterialTheme.colorScheme.onSurfaceVariant, "未習として記録")
                        }
                        Text(icon, style = MaterialTheme.typography.displayLarge)
                        Text(
                            label,
                            style = MaterialTheme.typography.headlineMedium,
                            color = color
                        )
                        Text(
                            "スコア: ${String.format("%.1f", state.score)} (${state.gradingMethod})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Correct answer
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("正解", style = MaterialTheme.typography.labelLarge)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    state.quiz.answer,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        // Explanation
                        if (!state.quiz.explanation.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("解説", style = MaterialTheme.typography.labelLarge)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        state.quiz.explanation,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                viewModel.nextQuestion()
                                answerInput = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text("次の問題へ")
                        }
                    }
                }

                is QuizViewModel.QuizUiState.SessionComplete -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🏆", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("セッション完了！", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "正解: ${state.correctCount} / ${state.totalAnswered}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "合計スコア: ${String.format("%.1f", state.totalScore)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { viewModel.startSession() }) {
                                Text("もう一度")
                            }
                            Button(onClick = onBack) {
                                Text("終了")
                            }
                        }
                    }
                }
            }
        }
    }
}
```

### `ui/screen/ImportScreen.kt`

```kotlin
package com.example.encyclopedia.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.encyclopedia.viewmodel.ImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importCsv(it) }
    }

    val mdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importMarkdown(it) }
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is ImportViewModel.ImportState.Done -> {
                Toast.makeText(
                    context,
                    "インポート完了: ${s.result.successCount}件成功, ${s.result.errorCount}件エラー",
                    Toast.LENGTH_LONG
                ).show()
            }
            is ImportViewModel.ImportState.Error -> {
                Toast.makeText(context, "エラー: ${s.message}", Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("インポート") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CSV Import
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📊 CSVインポート（単語帳）", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "列: term, reading(任意), definition, field(任意)\n" +
                        "ヘッダー行必須。UTF-8エンコーディング。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            csvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*"))
                        },
                        enabled = state !is ImportViewModel.ImportState.Importing
                    ) {
                        Text("CSVファイルを選択")
                    }
                }
            }

            // Markdown Import
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📝 Markdownインポート（メモ）", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "H1/H2見出しごとに1エントリーとして取り込みます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            mdLauncher.launch(arrayOf("text/markdown", "text/plain", "*/*"))
                        },
                        enabled = state !is ImportViewModel.ImportState.Importing
                    ) {
                        Text("Markdownファイルを選択")
                    }
                }
            }

            // Status
            when (val s = state) {
                is ImportViewModel.ImportState.Importing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("インポート中...")
                    }
                }
                is ImportViewModel.ImportState.Done -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("✅ 結果", style = MaterialTheme.typography.titleSmall)
                            Text("成功: ${s.result.successCount} 件")
                            if (s.result.errorCount > 0) {
                                Text(
                                    "エラー: ${s.result.errorCount} 件",
                                    color = MaterialTheme.colorScheme.error
                                )
                                s.result.errors.take(5).forEach { err ->
                                    Text(
                                        "  • $err",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
```

---

## 11. ナビゲーション更新

### `ui/navigation/NavGraph.kt`（Phase 1更新）

```kotlin
package com.example.encyclopedia.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.encyclopedia.ui.screen.*

object Routes {
    const val DASHBOARD = "dashboard"
    const val SEARCH = "search"
    const val SRS_REVIEW = "srs_review"
    const val QUIZ = "quiz"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val THOUGHT_NEW = "thought/new"
    const val THOUGHT_EDIT = "thought/edit/{entryId}"
    const val DEFINITION_NEW = "definition/new"
    const val DEFINITION_EDIT = "definition/edit/{entryId}"
    const val ENTRY_DETAIL = "entry/{entryId}"
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToEntry = { id -> navController.navigate("entry/$id") },
                onNavigateToNewThought = { navController.navigate(Routes.THOUGHT_NEW) },
                onNavigateToNewDefinition = { navController.navigate(Routes.DEFINITION_NEW) },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToSrs = { navController.navigate(Routes.SRS_REVIEW) },
                onNavigateToQuiz = { navController.navigate(Routes.QUIZ) },
                onNavigateToImport = { navController.navigate(Routes.IMPORT) }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEntry = { id -> navController.navigate("entry/$id") }
            )
        }

        composable(Routes.SRS_REVIEW) {
            SrsReviewScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.QUIZ) {
            QuizScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.IMPORT) {
            ImportScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.THOUGHT_NEW) {
            ThoughtEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate("entry/$id")
                }
            )
        }

        composable(
            route = Routes.THOUGHT_EDIT,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) {
            ThoughtEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Routes.DEFINITION_NEW) {
            DefinitionEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate("entry/$id")
                }
            )
        }

        composable(
            route = Routes.DEFINITION_EDIT,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) {
            DefinitionEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ENTRY_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) {
            EntryDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { type, entryId ->
                    when (type) {
                        "thought" -> navController.navigate("thought/edit/$entryId")
                        "definition" -> navController.navigate("definition/edit/$entryId")
                    }
                }
            )
        }
    }
}
```

---

## 12. MainActivity更新（BottomNavigation 4タブ化）

### `MainActivity.kt`（Phase 1更新）

```kotlin
package com.example.encyclopedia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.encyclopedia.ui.navigation.AppNavGraph
import com.example.encyclopedia.ui.navigation.Routes
import com.example.encyclopedia.ui.theme.EncyclopediaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EncyclopediaTheme {
                MainContent()
            }
        }
    }
}

@Composable
private fun MainContent() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val topLevelRoutes = listOf(
        Routes.DASHBOARD, Routes.SEARCH, Routes.SRS_REVIEW, Routes.QUIZ
    )
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("ホーム") },
                        selected = currentRoute == Routes.DASHBOARD,
                        onClick = {
                            if (currentRoute != Routes.DASHBOARD) {
                                navController.navigate(Routes.DASHBOARD) {
                                    popUpTo(Routes.DASHBOARD) { inclusive = true }
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("検索") },
                        selected = currentRoute == Routes.SEARCH,
                        onClick = {
                            if (currentRoute != Routes.SEARCH) {
                                navController.navigate(Routes.SEARCH) {
                                    popUpTo(Routes.DASHBOARD)
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.School, contentDescription = null) },
                        label = { Text("復習") },
                        selected = currentRoute == Routes.SRS_REVIEW,
                        onClick = {
                            if (currentRoute != Routes.SRS_REVIEW) {
                                navController.navigate(Routes.SRS_REVIEW) {
                                    popUpTo(Routes.DASHBOARD)
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = null) },
                        label = { Text("クイズ") },
                        selected = currentRoute == Routes.QUIZ,
                        onClick = {
                            if (currentRoute != Routes.QUIZ) {
                                navController.navigate(Routes.QUIZ) {
                                    popUpTo(Routes.DASHBOARD)
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            AppNavGraph(navController = navController)
        }
    }
}
```

---

## 13. Application更新（WorkManager初期化）

### `PersonalEncyclopediaApp.kt`（Phase 1更新）

```kotlin
package com.example.encyclopedia

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.encyclopedia.backup.BackupWorker
import com.example.encyclopedia.backup.PortableExportWorker
import com.example.encyclopedia.db.AppDatabase
import com.example.encyclopedia.db.SeedData
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PersonalEncyclopediaApp : Application(), Configuration.Provider {

    @Inject lateinit var database: AppDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Seed entry types
        appScope.launch {
            database.entryTypeDao().insertAll(SeedData.entryTypes)
        }

        // Schedule daily encrypted backup (§6.3)
        BackupWorker.schedule(this)

        // Schedule weekly portable export (§6.3 layer 2)
        PortableExportWorker.schedule(this)
    }
}
```

---

## 14. DashboardScreen更新（SRS/Quiz統計追加）

### `ui/screen/DashboardScreen.kt`（Phase 1差分）

```kotlin
// DashboardScreen の引数に追加:
//   onNavigateToSrs: () -> Unit,
//   onNavigateToQuiz: () -> Unit,
//   onNavigateToImport: () -> Unit,

// Stats セクションを以下に置き換え:
item {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            StatItem("合計", "$totalCount 件")
            StatItem("復習期限", "$dueCount 枚")
            StatItem("クイズ", "$quizCount 問")
        }
    }
}

// Quick actions (SRS / Quiz buttons)
item {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            onClick = onNavigateToSrs,
            modifier = Modifier.weight(1f)
        ) {
            Text("📖 復習 ($dueCount)")
        }
        FilledTonalButton(
            onClick = onNavigateToQuiz,
            modifier = Modifier.weight(1f)
        ) {
            Text("✏️ クイズ")
        }
    }
}

// DashboardViewModel に追加:
// val dueCount: StateFlow<Int> = srsRepo.observeDueCount()
//     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
// val quizCount: StateFlow<Int> = quizRepo.observeQuizCount()
//     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
```

---

## 15. 性能改善パッチ（Phase 0コードへの修正）

### `EntryCard.kt` — SimpleDateFormat キャッシュ

```kotlin
// 修正前:
private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}

// 修正後:
private val dateFormat = ThreadLocal.withInitial {
    SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
}

private fun formatDate(millis: Long): String {
    return dateFormat.get()!!.format(Date(millis))
}
```

### `SearchViewModel.kt` — distinctUntilChanged追加

```kotlin
val results: StateFlow<List<EntryEntity>> = _query
    .distinctUntilChanged()          // ← 追加
    .debounce(300)
    .combine(_typeFilter) { q, t -> q to t }
    .distinctUntilChanged()          // ← 追加
    .flatMapLatest { (q, typeFilter) ->
        // ... same as before
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

### `LocalServer.kt` — Constructor injection化

```kotlin
// Phase 0 の lateinit var を廃止し、Hilt で直接注入:
@Singleton
class LocalServer @Inject constructor(
    private val tokenManager: TokenManager,
    private val entryDao: EntryDao,
    private val thoughtDao: EntryThoughtDao,
    private val definitionDao: EntryDefinitionDao
) {
    // ... rest same, but remove lateinit vars and use injected DAOs directly
}
```

---

## 16. AndroidManifest更新

```xml
<!-- 追加パーミッション -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- WorkManager のデフォルト初期化を無効化 (HiltWorkerFactoryを使うため) -->
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

---

## 実装チェックリスト（Phase 1判定基準）

| # | 項目 | 設計書対応 | 状態 |
|---|---|---|---|
| 1 | `srs_review` + `SrsCurrentView` + SM-2 | §5.5.5, §8.6 | ✅ |
| 2 | SRS復習UI（カード型・ワンタップ評価） | §11.3 | ✅ |
| 3 | `quiz_bank` / `quiz_attempts` / `QuizMasteryView` | §5.6 | ✅ |
| 4 | 多段階採点（正規化→暦数値→Fuzzy→同義語→複数正解） | §8.4 | ✅ |
| 5 | ルールベースクイズ生成（QA/逆引き/MCQ/穴埋め） | §8.3 | ✅ |
| 6 | クイズ出題フロー（回答/未習→解説→次問題） | §8.1 | ✅ |
| 7 | 暗号化バックアップ（AES-256-GCM + Keystore） | §6.3 | ✅ |
| 8 | WorkManager日次バックアップ（Wi-Fi+充電中） | §6.3 | ✅ |
| 9 | 可搬エクスポート（CSV/JSON週次） | §6.3 | ✅ |
| 10 | CSVインポート（単語帳） | §12.1, §12.3 | ✅ |
| 11 | Markdownインポート（メモ） | §12.1, §12.3 | ✅ |
| 12 | DB Migration v1→v2 | — | ✅ |
| 13 | BottomNavigation 4タブ化 | §11.3 | ✅ |
| 14 | Phase 0性能改善（DateFormat/distinctUntilChanged/DI） | — | ✅ |

**Phase 1 判定基準**: 「単語帳とクイズを実際の学習に使い続けている」— 3日以上連続でSRS復習またはクイズ演習を行えばPhase 2へ移行可。