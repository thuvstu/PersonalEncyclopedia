package com.thuvstu.personalencyclopedia.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * G2 (GAP-2/6): v1→v7 の全マイグレーションチェーンを検証する。
 * - Round C2で復帰させたスキーマJSON(app/schemas)を使って起点DBを作成
 * - Round Eで追加した MIGRATION_6_7 (era_master) が含まれる
 * - マイグレーション後もPhase-0データが保持されること、era_masterのシードが投入済みであることを確認
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
        MIGRATION_6_7
    )

    @Test
    fun migrate1To7_preservesDataAndAddsEraMaster() {
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

        // 2. v1→v7 の全マイグレーションを適用し、v7スキーマ(7.json)と構造が一致することを検証
        helper.runMigrationsAndValidate(testDb, 1, true, *allMigrations).use { db ->
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
        }
    }

    @Test
    fun migrate2To7_works() {
        helper.createDatabase(testDb, 2).use { db ->
            db.execSQL(
                "INSERT INTO entry (id, type, title, content, summary, sourceUrl, lang, isFavorite, isMuted, metadataJson, createdAt, updatedAt) VALUES ('e2', 'thought', 'v2のデータ', null, null, null, 'ja', 0, 0, '{}', 1, 1)"
            )
            db.execSQL(
                "INSERT INTO topic (id, name) VALUES ('top1', 'トピック')"
            )
        }

        helper.runMigrationsAndValidate(
            testDb, 2, true,
            MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
        ).use { db ->
            val entryCount = db.query("SELECT COUNT(*) FROM entry WHERE id = 'e2'").use { c ->
                c.moveToFirst(); c.getInt(0)
            }
            assertEquals(1, entryCount)

            val topicCount = db.query("SELECT COUNT(*) FROM topic WHERE id = 'top1'").use { c ->
                c.moveToFirst(); c.getInt(0)
            }
            assertEquals(1, topicCount)
        }
    }

    @Test
    fun migrate6To7_addsEraMaster() {
        helper.createDatabase(testDb, 6).use { db ->
            db.execSQL(
                "INSERT INTO entry (id, type, title, content, summary, sourceUrl, lang, isFavorite, isMuted, metadataJson, createdAt, updatedAt) VALUES ('e3', 'thought', 'v6のデータ', null, null, null, 'ja', 0, 0, '{}', 1, 1)"
            )
        }

        helper.runMigrationsAndValidate(testDb, 6, true, MIGRATION_6_7).use { db ->
            val eraCount = db.query("SELECT COUNT(*) FROM era_master").use { c ->
                c.moveToFirst(); c.getInt(0)
            }
            assertEquals("v6→v7でera_masterが追加される", 56, eraCount)
        }
    }
}
