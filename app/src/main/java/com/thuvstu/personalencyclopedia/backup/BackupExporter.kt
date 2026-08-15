// app/src/main/java/com/thuvstu/personalencyclopedia/backup/BackupExporter.kt
package com.thuvstu.personalencyclopedia.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.thuvstu.personalencyclopedia.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase
) {
    /**
     * ★H-4(a): SAFで選択されたフォルダ(ツリーURI)に暗号化バックアップを書き込む。
     * Drive APIキー不要。DocumentsProvider経由で直接書き込み可能。
     */
    suspend fun exportToSaf(treeUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. WALを本体に統合
            database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")

            // 2. DBファイル確認
            val dbFile = context.getDatabasePath("encyclopedia.db")
            if (!dbFile.exists()) return@withContext Result.failure(Exception("DB file not found"))

            // 3. 一時コピー
            val tempDir = File(context.cacheDir, "backup_temp").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val plainCopy = File(tempDir, "encyclopedia_$timestamp.db")
            dbFile.copyTo(plainCopy, overwrite = true)

            // 4. 暗号化
            val encryptedFile = File(tempDir, "encyclopedia_$timestamp.db.enc")
            BackupEncryptor.encrypt(plainCopy, encryptedFile)

            // 5. ツリーURI内に新規ドキュメント作成
            val fileName = "encyclopedia_$timestamp.db.enc"
            val docUri = createDocumentInTree(treeUri, fileName)
                ?: return@withContext Result.failure(Exception("フォルダ内にファイルを作成できませんでした"))

            // 6. 書き込み
            context.contentResolver.openOutputStream(docUri)?.use { out ->
                encryptedFile.inputStream().use { it.copyTo(out) }
            } ?: return@withContext Result.failure(Exception("出力ストリームを開けません"))

            // 7. クリーンアップ
            plainCopy.delete()
            encryptedFile.delete()

            Result.success("バックアップを保存しました: $fileName")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 暗号化済みファイルをツリーURI直下に出力する。
     */
    suspend fun writeEncryptedFileToTree(treeUri: Uri, encryptedFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = encryptedFile.name
            val docUri = createDocumentInTree(treeUri, fileName)
                ?: return@withContext Result.failure(Exception("SAFフォルダ内にファイルを作成できませんでした"))

            context.contentResolver.openOutputStream(docUri)?.use { out ->
                encryptedFile.inputStream().use { it.copyTo(out) }
            } ?: return@withContext Result.failure(Exception("出力ストリームを開けません"))

            Result.success("SAFフォルダへバックアップを保存しました: $fileName")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 暗号化バックアップファイル（.enc）からDBを復元する（B5）
     */
    suspend fun restoreFromEncryptedUri(sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "restore_temp").apply { mkdirs() }
        val tempEnc = File(tempDir, "restore_target.enc")
        val tempDb = File(tempDir, "restore_target.db")
        try {
            // 1. 暗号化データを一時ファイルへコピー
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempEnc.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(Exception("入力ストリームを開けません"))

            // 2. 復号
            BackupEncryptor.decrypt(tempEnc, tempDb)

            // 3. SQLiteヘッダ検証
            val header = ByteArray(16)
            tempDb.inputStream().use { it.read(header) }
            val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
            if (!header.contentEquals(sqliteHeader)) {
                return@withContext Result.failure(Exception("復号されたファイルは有効なSQLiteデータベースではありません"))
            }

            // 4. 既存DBのWALチェックポイント
            runCatching {
                database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            }

            // 5. DBファイル差し替え
            val targetDb = context.getDatabasePath("encyclopedia.db")
            val walFile = File(targetDb.path + "-wal")
            val shmFile = File(targetDb.path + "-shm")
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            tempDb.copyTo(targetDb, overwrite = true)

            Result.success("データベースの復元が完了しました。")
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempEnc.delete()
            tempDb.delete()
        }
    }

    /**
     * ツリーURIのルート直下にドキュメントを作成する。
     * OpenDocumentTree で得たURIはツリーURIなので、
     * buildDocumentUriUsingTree で親ドキュメントURIに変換してから createDocument する。
     */
    private fun createDocumentInTree(treeUri: Uri, displayName: String): Uri? {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                "application/octet-stream",
                displayName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}