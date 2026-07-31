package com.thuvstu.personalencyclopedia.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
import com.thuvstu.personalencyclopedia.db.dao.TagDao
import com.thuvstu.personalencyclopedia.db.entity.EntryDefinitionEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.db.entity.TagEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class PortableExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val entryDao: EntryDao,
    private val definitionDao: EntryDefinitionDao,
    private val tagDao: TagDao
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "weekly_portable_export"
        private const val TAG = "PortableExport"

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
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

            // ── suspend コンテキストで全データ事前取得 ──
            val entries = entryDao.observeAll(limit = 100_000).first()
            val defs = definitionDao.search("", limit = 100_000).first()
            val tagsMap: Map<String, List<TagEntity>> = entries.associate { e ->
                e.id to tagDao.observeTagsForEntry(e.id).first()
            }

            // ── 以下は純粋関数（suspend 不要）──
            exportMarkdown(File(exportDir, "encyclopedia_$dateStr.md"), entries, tagsMap)
            exportCsv(File(exportDir, "definitions_$dateStr.csv"), defs)
            exportJson(File(exportDir, "entries_$dateStr.json"), entries, tagsMap)

            Log.i(TAG, "Exported ${entries.size} entries, ${defs.size} definitions")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            Result.failure()
        }
    }

    // ── Markdown（純粋関数）──

    private fun exportMarkdown(
        file: File,
        entries: List<EntryEntity>,
        tagsMap: Map<String, List<TagEntity>>
    ) {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        file.bufferedWriter().use { w ->
            w.write("# Personal Encyclopedia — Portable Export\n\n")
            w.write("- 出力日時: $now\n")
            w.write("- エントリー数: ${entries.size}\n\n")

            for (e in entries) {
                val tags = tagsMap[e.id].orEmpty()
                w.write("---\n\n")
                w.write("## [${e.type}] ${e.title}\n\n")
                w.write("- ID: `${e.id}`\n")
                w.write("- 作成: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(e.createdAt))}\n")
                if (tags.isNotEmpty()) {
                    w.write("- タグ: ${tags.joinToString(", ") { it.name }}\n")
                }
                e.sourceUrl?.let { w.write("- ソース: $it\n") }
                if (!e.content.isNullOrBlank()) {
                    w.write("\n${e.content}\n")
                }
                w.write("\n")
            }
        }
    }

    // ── CSV（純粋関数）──

    private fun exportCsv(file: File, defs: List<EntryDefinitionEntity>) {
        file.bufferedWriter().use { w ->
            w.write("term,reading,definition,field\n")
            defs.forEach { d ->
                w.write("${esc(d.term)},${esc(d.reading ?: "")},${esc(d.definition)},${esc(d.field ?: "")}\n")
            }
        }
    }

    // ── JSON（純粋関数）──

    private fun exportJson(
        file: File,
        entries: List<EntryEntity>,
        tagsMap: Map<String, List<TagEntity>>
    ) {
        val jsonArray = buildJsonArray {
            for (e in entries) {
                addJsonObject {
                    put("id", e.id)
                    put("type", e.type)
                    put("title", e.title)
                    e.content?.let { put("content", it) }
                    e.summary?.let { put("summary", it) }
                    e.sourceUrl?.let { put("sourceUrl", it) }
                    put("isFavorite", e.isFavorite)
                    put("isMuted", e.isMuted)
                    put("createdAt", e.createdAt)
                    put("updatedAt", e.updatedAt)
                    val tags = tagsMap[e.id].orEmpty()
                    if (tags.isNotEmpty()) {
                        putJsonArray("tags") { tags.forEach { add(it.name) } }
                    }
                }
            }
        }
        val prettyJson = Json { prettyPrint = true }
            .encodeToString(JsonElement.serializer(), jsonArray)
        file.writeText(prettyJson)
    }

    private fun esc(value: String): String {
        return if (value.contains(",") || value.contains("\"") ||
            value.contains("\n") || value.contains("\r")
        ) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }
}