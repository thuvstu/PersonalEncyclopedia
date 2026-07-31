// 📁 app/src/main/java/com/thuvstu/personalencyclopedia/importer/DocumentExtractor.kt
package com.thuvstu.personalencyclopedia.importer

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentExtractor @Inject constructor(private val context: Context) {

    fun extractText(uri: Uri, mimeType: String): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            when {
                mimeType.contains("pdf") -> extractPdf(inputStream)
                mimeType.contains("wordprocessingml") || mimeType.contains("docx") -> extractDocx(inputStream)
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractPdf(inputStream: java.io.InputStream): String {
        PDDocument.load(inputStream).use { document ->
            val stripper = PDFTextStripper()
            return stripper.getText(document)
        }
    }

    private fun extractDocx(inputStream: java.io.InputStream): String {
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xmlContent = zis.bufferedReader().readText()
                    // JsoupでXMLパースし、テキストノードのみ抽出
                    val doc = Jsoup.parse(xmlContent, "", org.jsoup.parser.Parser.xmlParser())
                    return doc.text()
                }
                entry = zis.nextEntry
            }
        }
        return ""
    }
}