package com.thuvstu.personalencyclopedia.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import com.thuvstu.personalencyclopedia.db.dao.EntryDefinitionDao
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