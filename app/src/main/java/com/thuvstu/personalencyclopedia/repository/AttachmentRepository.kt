package com.thuvstu.personalencyclopedia.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.thuvstu.personalencyclopedia.db.dao.EntryAttachmentDao
import com.thuvstu.personalencyclopedia.db.entity.EntryAttachmentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentDao: EntryAttachmentDao
) {
    companion object { private const val TAG = "AttachmentRepo" }

    fun observeForEntry(entryId: String): Flow<List<EntryAttachmentEntity>> =
        attachmentDao.observeForEntry(entryId)

    /** URI → blobs/ に物理コピー → DB登録。失敗時は null。 */
    suspend fun importFromUri(entryId: String, uri: Uri, caption: String? = null): String? =
        withContext(Dispatchers.IO) {
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
                val dir = File(context.filesDir, "blobs/attachments/$entryId")
                dir.mkdirs()
                val file = File(dir, "${UUID.randomUUID()}.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                val id = UUID.randomUUID().toString()
                attachmentDao.insert(
                    EntryAttachmentEntity(
                        id = id,
                        entryId = entryId,
                        blobPath = file.absolutePath,
                        mimeType = mimeType,
                        caption = caption,
                        sortOrder = attachmentDao.countForEntry(entryId)
                    )
                )
                id
            } catch (e: Exception) {
                Log.e(TAG, "Attachment import failed", e)
                null
            }
        }

    /** 物理ファイルごと削除 */
    suspend fun remove(attachment: EntryAttachmentEntity) = withContext(Dispatchers.IO) {
        File(attachment.blobPath).delete()
        attachmentDao.delete(attachment.id)
    }
}