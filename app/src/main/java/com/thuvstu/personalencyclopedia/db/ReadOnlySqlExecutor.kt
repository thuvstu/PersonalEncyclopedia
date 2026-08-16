package com.thuvstu.personalencyclopedia.db

import android.database.Cursor
import com.thuvstu.personalencyclopedia.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQL Explorer 用の読み取り専用SQL実行器（設計書§11.12）。
 *
 * 二重の防御:
 * 1. 先頭トークンが SELECT / WITH 以外なら拒否
 * 2. 実行前にその接続へ `PRAGMA query_only = ON` を適用し、終了時に必ず OFF へ戻す
 *    （Roomは既定で単一コネクションのため、PRAGMA適用〜クエリ実行〜解除の間は
 *     他のDAO操作が割り込めない。finallyで必ず解除してアプリ本体の書き込みを保護する）
 *
 * 用途はデバッグ・DB管理画面からのみ。通常のユーザー導線には置かない。
 */
class ReadOnlySqlExecutor(private val database: AppDatabase) {

    sealed class SqlExecutionResult {
        data class Success(
            val columns: List<String>,
            val rows: List<List<String>>,
            val elapsedMs: Long
        ) : SqlExecutionResult()

        data class Error(val message: String) : SqlExecutionResult()
    }

    companion object {
        private val writeKeywords = setOf(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE", "REPLACE", "PRAGMA",
            "ATTACH", "DETACH", "VACUUM", "REINDEX", "ANALYZE", "BEGIN", "COMMIT", "ROLLBACK",
            "SAVEPOINT", "RELEASE"
        )
    }

    suspend fun executeReadOnly(sql: String): SqlExecutionResult = withContext(Dispatchers.IO) {
        val trimmed = sql.trim()
        if (trimmed.isBlank()) {
            return@withContext SqlExecutionResult.Error("SQLが空です")
        }
        val firstToken = trimmed.split(Regex("\\s+")).firstOrNull()?.uppercase() ?: ""
        if (firstToken != "SELECT" && firstToken != "WITH") {
            return@withContext SqlExecutionResult.Error("読み取り専用です（SELECT / WITH のみ実行可能）")
        }
        // コメント・文字列リテラルを除去した上で、本文に書き込み系キーワードが紛れていないか確認
        val stripped = stripCommentsAndStrings(trimmed)
        val tokens = stripped.split(Regex("\\s+")).map { it.uppercase() }
        val hiddenWrite = tokens.any { it in writeKeywords }
        if (hiddenWrite) {
            return@withContext SqlExecutionResult.Error("書き込み系ステートメント・PRAGMAは実行できません（読み取り専用）")
        }

        val db = database.openHelper.writableDatabase
        val start = System.currentTimeMillis()
        try {
            db.execSQL("PRAGMA query_only = ON")
            db.query(trimmed).use { cursor ->
                val columns = cursor.columnNames.toList()
                val rows = ArrayList<List<String>>()
                while (cursor.moveToNext()) {
                    rows.add(columns.indices.map { idx -> cursor.getStringOrNull(idx) ?: "NULL" })
                    if (rows.size >= 500) break // 表示上限（結果が大きすぎるクエリの保護）
                }
                SqlExecutionResult.Success(columns, rows, System.currentTimeMillis() - start)
            }
        } catch (e: Exception) {
            AppLogger.e("SqlExplorer", "クエリ失敗", e)
            SqlExecutionResult.Error(e.message ?: "クエリに失敗しました")
        } finally {
            runCatching { db.execSQL("PRAGMA query_only = OFF") }
        }
    }

    /** テーブル/ビューの一覧（スキーマブラウザ用）。 */
    suspend fun listTables(): List<SchemaObject> = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        val result = ArrayList<SchemaObject>()
        db.query(
            "SELECT name, type FROM sqlite_master " +
                "WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' " +
                "AND name NOT LIKE 'android_%' ORDER BY type, name"
        ).use { c ->
            while (c.moveToNext()) {
                result.add(
                    SchemaObject(
                        name = c.getString(0),
                        type = if (c.getString(1) == "table") "テーブル" else "ビュー"
                    )
                )
            }
        }
        result
    }

    /** PRAGMA table_info（スキーマブラウザのカラム一覧用）。テーブル名はマスタ一覧で検証済みのもののみ。 */
    suspend fun tableInfo(tableName: String): List<ColumnInfo> = withContext(Dispatchers.IO) {
        if (!tableName.matches(Regex("[A-Za-z0-9_]+"))) return@withContext emptyList()
        val db = database.openHelper.readableDatabase
        val result = ArrayList<ColumnInfo>()
        db.query("PRAGMA table_info(\"$tableName\")").use { c ->
            while (c.moveToNext()) {
                result.add(
                    ColumnInfo(
                        name = c.getStringOrNull(1) ?: "",
                        type = c.getStringOrNull(2) ?: "",
                        notNull = c.getIntOrNull(3) == 1,
                        primaryKey = c.getIntOrNull(5) == 1
                    )
                )
            }
        }
        result
    }

    /** DB統計（journal_mode / page_count / page_size / freelist_count）。 */
    suspend fun dbStats(): Map<String, String> = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        val stats = LinkedHashMap<String, String>()
        listOf("journal_mode", "page_count", "page_size", "freelist_count").forEach { pragma ->
            db.query("PRAGMA $pragma").use { c ->
                if (c.moveToFirst()) stats[pragma] = c.getStringOrNull(0) ?: ""
            }
        }
        stats
    }

    /** 整合性チェック（実行に時間がかかる場合がある）。 */
    suspend fun integrityCheck(): String = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        db.query("PRAGMA integrity_check(1)").use { c ->
            if (c.moveToFirst()) c.getStringOrNull(0) ?: "unknown" else "unknown"
        }
    }

    private fun stripCommentsAndStrings(sql: String): String {
        // 簡易的な除去: '...' 文字列と -- コメント、/* */ コメントを空白へ置換
        var s = sql.replace(Regex("'[^']*'"), " ")
        s = s.replace(Regex("--[^\\n]*"), " ")
        s = s.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        return s
    }

    data class SchemaObject(val name: String, val type: String)
    data class ColumnInfo(val name: String, val type: String, val notNull: Boolean, val primaryKey: Boolean)

    private fun Cursor.getStringOrNull(idx: Int): String? =
        if (isNull(idx)) null else getString(idx)

    private fun Cursor.getIntOrNull(idx: Int): Int? =
        if (isNull(idx)) null else getInt(idx)
}
