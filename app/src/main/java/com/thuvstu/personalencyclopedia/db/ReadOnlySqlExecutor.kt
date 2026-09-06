package com.thuvstu.personalencyclopedia.db

import androidx.room.execSQL
import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteStatement
import com.thuvstu.personalencyclopedia.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SQL Explorer 用の読み取り専用SQL実行器（設計書§11.12）。
 *
 * 二重の防御:
 * 1. 先頭トークンが SELECT / WITH 以外なら拒否
 * 2. `useReaderConnection` 上で `PRAGMA query_only = ON` を適用してから prepare/step
 *    （wt47 で BundledSQLiteDriver にしたため openHelper.writable/readableDatabase は旧APIで例外になる）
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

        /**
         * 実行前ゲート。null なら実行可、非nullは拒否理由。
         * Room を必要としないので JVM テストから直接検証できる。
         */
        fun denyReason(sql: String): String? {
            val trimmed = sql.trim()
            if (trimmed.isBlank()) return "SQLが空です"
            val firstToken = trimmed.split(Regex("\\s+")).firstOrNull()?.uppercase() ?: ""
            if (firstToken != "SELECT" && firstToken != "WITH") {
                return "読み取り専用です（SELECT / WITH のみ実行可能）"
            }
            val stripped = stripCommentsAndStrings(trimmed)
            val tokens = stripped.split(Regex("\\s+")).map { it.uppercase() }
            if (tokens.any { it in writeKeywords }) {
                return "書き込み系ステートメント・PRAGMAは実行できません（読み取り専用）"
            }
            return null
        }

        internal fun stripCommentsAndStrings(sql: String): String {
            // 簡易的な除去: '...' 文字列と -- コメント、/* */ コメントを空白へ置換
            var s = sql.replace(Regex("'[^']*'"), " ")
            s = s.replace(Regex("--[^\\n]*"), " ")
            s = s.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            return s
        }
    }

    suspend fun executeReadOnly(sql: String): SqlExecutionResult = withContext(Dispatchers.IO) {
        denyReason(sql)?.let { return@withContext SqlExecutionResult.Error(it) }
        val trimmed = sql.trim()
        val start = System.currentTimeMillis()
        try {
            database.useReaderConnection { conn ->
                // 読み取り接続に query_only を掛けて書き込みを二重に封じる。
                // プーリングされた reader を書き込み可能に戻さないよう OFF にはしない。
                runCatching { conn.execSQL("PRAGMA query_only = ON") }
                conn.usePrepared(trimmed) { stmt ->
                    val columns = stmt.columnNames()
                    val rows = ArrayList<List<String>>()
                    while (stmt.step()) {
                        rows.add(columns.indices.map { idx -> stmt.stringOrNull(idx) ?: "NULL" })
                        if (rows.size >= 500) break // 表示上限（結果が大きすぎるクエリの保護）
                    }
                    SqlExecutionResult.Success(columns, rows, System.currentTimeMillis() - start)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("SqlExplorer", "クエリ失敗", e)
            SqlExecutionResult.Error(e.message ?: "クエリに失敗しました")
        }
    }

    /** テーブル/ビューの一覧（スキーマブラウザ用）。 */
    suspend fun listTables(): List<SchemaObject> = withContext(Dispatchers.IO) {
        database.useReaderConnection { conn ->
            conn.usePrepared(
                "SELECT name, type FROM sqlite_master " +
                    "WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' " +
                    "AND name NOT LIKE 'android_%' ORDER BY type, name"
            ) { stmt ->
                val result = ArrayList<SchemaObject>()
                while (stmt.step()) {
                    result.add(
                        SchemaObject(
                            name = stmt.stringOrNull(0) ?: "",
                            type = if (stmt.stringOrNull(1) == "table") "テーブル" else "ビュー"
                        )
                    )
                }
                result
            }
        }
    }

    /** PRAGMA table_info（スキーマブラウザのカラム一覧用）。テーブル名はマスタ一覧で検証済みのもののみ。 */
    suspend fun tableInfo(tableName: String): List<ColumnInfo> = withContext(Dispatchers.IO) {
        if (!tableName.matches(Regex("[A-Za-z0-9_]+"))) return@withContext emptyList()
        database.useReaderConnection { conn ->
            conn.usePrepared("PRAGMA table_info(\"$tableName\")") { stmt ->
                val result = ArrayList<ColumnInfo>()
                while (stmt.step()) {
                    result.add(
                        ColumnInfo(
                            name = stmt.stringOrNull(1) ?: "",
                            type = stmt.stringOrNull(2) ?: "",
                            notNull = stmt.longOrNull(3) == 1L,
                            primaryKey = stmt.longOrNull(5) == 1L
                        )
                    )
                }
                result
            }
        }
    }

    /** DB統計（journal_mode / page_count / page_size / freelist_count）。 */
    suspend fun dbStats(): Map<String, String> = withContext(Dispatchers.IO) {
        database.useReaderConnection { conn ->
            val stats = LinkedHashMap<String, String>()
            listOf("journal_mode", "page_count", "page_size", "freelist_count").forEach { pragma ->
                conn.usePrepared("PRAGMA $pragma") { stmt ->
                    if (stmt.step()) stats[pragma] = stmt.stringOrNull(0) ?: ""
                }
            }
            stats
        }
    }

    /** 整合性チェック（実行に時間がかかる場合がある）。 */
    suspend fun integrityCheck(): String = withContext(Dispatchers.IO) {
        database.useReaderConnection { conn ->
            conn.usePrepared("PRAGMA integrity_check(1)") { stmt ->
                if (stmt.step()) stmt.stringOrNull(0) ?: "unknown" else "unknown"
            }
        }
    }

    data class SchemaObject(val name: String, val type: String)
    data class ColumnInfo(val name: String, val type: String, val notNull: Boolean, val primaryKey: Boolean)
}

private fun SQLiteStatement.columnNames(): List<String> =
    (0 until getColumnCount()).map { getColumnName(it) }

private fun SQLiteStatement.stringOrNull(idx: Int): String? {
    if (isNull(idx)) return null
    return runCatching { getText(idx) }.getOrNull()
        ?: runCatching { getLong(idx).toString() }.getOrNull()
        ?: runCatching { getDouble(idx).toString() }.getOrNull()
}

private fun SQLiteStatement.longOrNull(idx: Int): Long? =
    if (isNull(idx)) null else runCatching { getLong(idx) }.getOrNull()
