package com.thuvstu.personalencyclopedia.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G2 (GAP-2/6) + walkthrough4 Round1: v1→v8 の全マイグレーションチェーンを検証する。
 * - Round C2で復帰させたスキーマJSON(app/schemas)を使って起点DBを作成
 * - Round Eで追加した MIGRATION_6_7 (era_master) が含まれる
 * - walkthrough4で追加した MIGRATION_7_8 (entry_custom_field / repetitionCount / answeredWithinMs) が含まれる
 * - 注意: runMigrationsAndValidate の version は「終了バージョン」。walkthrough4で v1→v7 系の引数を修正した。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList()
    )

    private val allMigrations = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8
    )

    @Test
    fun migrate1To8_preservesDataAndAddsEraMasterAndV8() {
        // 1. v1 スキーマ(1.json)でDBを作成し、Phase-0データを投入
        helper.createDatabase(testDb, 1).use { db ->
            db.execSQL(
                "INSERT INTO entry_type (name, labelJa, icon, colorHex, isActive, sortOrder) VALUES ('thought', 'メモ・思考', null, '#8B5CF6', 1, 0)"
            )
            db.execSQL(
                "INSERT INTO entry (id, type, title, content, summary, sourceUrl, lang, isFavorite, isMuted, metadataJson, createdAt, updatedAt) VALUES ('e1', 'thought', 'マイグレーションテスト', '本文', null, null, 'ja', 0, 0, '{}', 1, 1)"
            )
            db.execSQL(
                "INSERT INTO entry_thought (entryId, mood, context, isDraft) VALUES ('e1', null, null, 0)"
            )
            db.execSQL("INSERT INTO tag (id, name, colorHex) VALUES ('t1', 'テスト', '#FF0000')")
            db.execSQL("INSERT INTO entry_tag (entryId, tagId) VALUES ('e1', 't1')")
        }

        // 2. v1→v8 の全マイグレーションを適用し、v8スキーマ(8.json)と構造が一致することを検証
        helper.runMigrationsAndValidate(testDb, 8, true, *allMigrations).use { db ->
            // Phase-0データが保持されている
            val entryCount = db.query("SELECT COUNT(*) FROM entry WHERE id = 'e1'").use { c ->
                c.moveToFirst(); c.getInt(0)
            }
            assertEquals("v1で投入したentryが残っていること", 1, entryCount)

            val tagJoinCount = db.query(
                "SELECT COUNT(*) FROM entry_tag WHERE entryId = 'e1' AND tagId = 't1'"
            ).use { c ->
                c.moveToFirst(); c.getInt(0)
            }
            assertEquals("entry_tagの関連が残っていること", 1, tagJoinCount)

            // Round E で追加した era_master がシード投入済み
            val keicho = db.query(
                "SELECT startYear FROM era_master WHERE name = '慶長'"
            ).use { c ->
                c.moveToFirst(); c.getInt(0)
            }
            assertEquals("慶長(1596)がシードされていること", 1596, keicho)

            val reiwa = db.query(
                "SELECT endYear FROM era_master WHERE name = '令和'"
            ).use { c ->
                c.moveToFirst(); c.isNull(0)
            }
            assertEquals("令和のendYearがnullであること", true, reiwa)

            val eraCount = db.query("SELECT COUNT(*) FROM era_master").use { c ->
                c.moveToFirst(); c.getInt(0)
            }
            assertEquals("era_masterシード件数", 56, eraCount)

            // walkthrough4 Round1: v8 の新規テーブル/カラム
            val customFieldTable = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='entry_custom_field'"
            ).use { c -> c.moveToFirst(); c.getCount() }
            assertEquals("entry_custom_fieldが存在すること", 1, customFieldTable)

            val repetitionColumn = db.query(
                "SELECT COUNT(*) FROM pragma_table_info('srs_review') WHERE name = 'repetitionCount'"
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            assertEquals("srs_review.repetitionCountが存在すること", 1, repetitionColumn)

            val answeredColumn = db.query(
                "SELECT COUNT(*) FROM pragma_table_info('quiz_attempts') WHERE name = 'answeredWithinMs'"
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            assertEquals("quiz_attempts.answeredWithinMsが存在すること", 1, answeredColumn)

            val viewColumn = db.query(
                "SELECT COUNT(*) FROM pragma_table_info('SrsCurrentView') WHERE name = 'repetitionCount'"
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            assertEquals("SrsCurrentViewにrepetitionCountが含まれること", 1, viewColumn)
        }
    }

    @Test
    fun migrate2To8_works() {
        helper.createDatabase(testDb, 2).use { db ->
            db.execSQL(
                "INSERT INTO entry (id, type, title, content, summary, sourceUrl, lang, isFavorite, isMuted, metadataJson, createdAt, updatedAt) VALUES ('e2', 'thought', 'v2のデータ', null, null, null, 'ja', 0, 0, '{}', 1, 1)"
            )
            db.execSQL(
                "INSERT INTO topic (id, name) VALUES ('top1', 'トピック')"
            )
        }

        helper.runMigrationsAndValidate(
            testDb, 8, true,
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
        ).use { db ->
            val entryCount = db.query("SELECT COUNT(*) FROM entry WHERE id = 'e2'").use { c ->
                c.moveToFirst(); c.getInt(0)
            }
            assertEquals(1, entryCount)

            val topicCount = db.query("SELECT COUNT(*) FROM topic WHERE id = 'top1'").use { c ->
                c.moveToFirst(); c.getInt(0)
            }
            assertEquals(1, topicCount)

            val v8TableCount = db.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='entry_custom_field'"
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            assertEquals("v2→v8でentry_custom_fieldが追加される", 1, v8TableCount)
        }
    }

    @Test
    fun migrate6To8_addsEraMaster() {
        helper.createDatabase(testDb, 6).use { db ->
            db.execSQL(
                "INSERT INTO entry (id, type, title, content, summary, sourceUrl, lang, isFavorite, isMuted, metadataJson, createdAt, updatedAt) VALUES ('e3', 'thought', 'v6のデータ', null, null, null, 'ja', 0, 0, '{}', 1, 1)"
            )
        }

        helper.runMigrationsAndValidate(testDb, 8, true, MIGRATION_6_7, MIGRATION_7_8).use { db ->
            val eraCount = db.query("SELECT COUNT(*) FROM era_master").use { c ->
                c.moveToFirst(); c.getInt(0)
            }
            assertEquals("v6→v8でera_masterが追加される", 56, eraCount)

            val repetitionColumn = db.query(
                "SELECT COUNT(*) FROM pragma_table_info('srs_review') WHERE name = 'repetitionCount'"
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            assertEquals("v6→v8でrepetitionCountが追加される", 1, repetitionColumn)
        }
    }

    @Test
    fun migrate7To8_addsCustomFieldAndColumns() {
        // v7 スキーマ(7.json)でDBを作成し、レビュー/クイズ回答を投入
        helper.createDatabase(testDb, 7).use { db ->
            db.execSQL(
                "INSERT INTO entry (id, type, title, content, summary, sourceUrl, lang, isFavorite, isMuted, metadataJson, createdAt, updatedAt) VALUES ('e1', 'definition', 'カスタムフィールド', '本文', null, null, 'ja', 0, 0, '{}', 1, 1)"
            )
            db.execSQL(
                "INSERT INTO entry_definition (entryId, term, definition, reading, field) VALUES ('e1', 'カスタム', '定義', null, 'tech')"
            )
            db.execSQL(
                "INSERT INTO srs_review (id, entryId, reviewedAt, grade, intervalDays, easeFactor, nextReviewAt) VALUES ('r1', 'e1', 100, 4, 6, 2.5, 200)"
            )
            db.execSQL(
                "INSERT INTO quiz_bank (id, quizType, question, choicesJson, answer, hintsJson, imagesJson, generationMethod, difficulty, isActive, createdAt) VALUES ('q1', 'qa', 'Q', '[]', 'A', '[]', '{}', 'rule_based', 3, 1, 100)"
            )
            db.execSQL(
                "INSERT INTO quiz_attempts (id, quizId, userAnswer, isCorrect, score, gradingMethod, hintsRevealed, attemptedAt) VALUES ('a1', 'q1', 'A', 1, 1.0, 'exact', 0, 100)"
            )
        }

        // v7→v8 を適用し、8.json と構造が一致することを検証
        helper.runMigrationsAndValidate(testDb, 8, true, MIGRATION_7_8).use { db ->
            // 既存行の repetitionCount は DEFAULT 0
            val repetitionCount = db.query(
                "SELECT repetitionCount FROM srs_review WHERE id = 'r1'"
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            assertEquals("既存レビューのrepetitionCountが0", 0, repetitionCount)

            // 既存行の answeredWithinMs は null のまま
            val answeredWithinMsNull = db.query(
                "SELECT answeredWithinMs FROM quiz_attempts WHERE id = 'a1'"
            ).use { c -> c.moveToFirst(); c.isNull(0) }
            assertEquals("既存クイズ回答のansweredWithinMsがnull", true, answeredWithinMsNull)

            // entry_custom_field に書き込み・読み出しができる
            db.execSQL(
                "INSERT INTO entry_custom_field (id, entryId, fieldName, fieldValue, sortOrder) VALUES ('cf1', 'e1', '別名', 'カスタム', 0)"
            )
            val customFieldCount = db.query(
                "SELECT COUNT(*) FROM entry_custom_field WHERE entryId = 'e1'"
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            assertEquals("カスタムフィールドが投入できること", 1, customFieldCount)

            // SrsCurrentView が repetitionCount を返す
            val viewRepetitionCount = db.query(
                "SELECT repetitionCount FROM SrsCurrentView WHERE entryId = 'e1'"
            ).use { c -> c.moveToFirst(); c.getInt(0) }
            assertEquals("SrsCurrentViewがrepetitionCountを返すこと", 0, viewRepetitionCount)
        }
    }
}
