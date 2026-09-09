package com.thuvstu.personalencyclopedia.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ★往復対称(mismatch §6-2): EntryExporter.buildJson の出力形式を EntryJsonCodec が欠落なく復元することを検証。
 * 純粋関数のためDB不要。
 */
class EntryJsonCodecTest {

    private fun decode(json: String, keepId: Boolean = true) =
        EntryJsonCodec.decode(Json.parseToJsonElement(json).jsonObject, keepId = keepId, newId = "NEW", now = 999L)

    @Test
    fun definition_roundtrip_keepsIdTimesTagsAndExtension() {
        val d = decode(
            """
            {"id":"e1","type":"definition","title":"再帰","content":"本文","summary":"要約","sourceUrl":null,
             "lang":"ja","isFavorite":true,"isMuted":false,"metadataJson":"{\"k\":1}","accessedAt":5,
             "createdAt":100,"updatedAt":200,"tags":["CS","アルゴリズム"],
             "extension":{"term":"再帰","definition":"自分自身を呼ぶ","reading":"さいき","field":"CS",
                          "examplesJson":"[\"fib\"]","relatedTermsJson":"[\"基底ケース\"]"}}
            """.trimIndent()
        )
        assertNotNull(d); d!!
        assertEquals("e1", d.entry.id)
        assertEquals(100L, d.entry.createdAt)
        assertEquals(200L, d.entry.updatedAt)
        assertEquals(5L, d.entry.accessedAt)
        assertEquals(true, d.entry.isFavorite)
        assertEquals("ja", d.entry.lang)
        assertEquals("{\"k\":1}", d.entry.metadataJson)
        assertEquals(listOf("CS", "アルゴリズム"), d.tags)
        assertEquals("さいき", d.definition?.reading)
        assertEquals("[\"fib\"]", d.definition?.examplesJson)
        assertEquals("[\"基底ケース\"]", d.definition?.relatedTermsJson)
    }

    @Test
    fun newIdIsUsedWhenNotKeeping() {
        val d = decode("""{"id":"e1","type":"thought","title":"t"}""", keepId = false)!!
        assertEquals("NEW", d.entry.id)
        assertEquals("NEW", d.thought?.entryId)
        assertEquals(999L, d.entry.createdAt)
    }

    @Test
    fun legacyExportWithoutNewFieldsStillDecodes() {
        // 旧exporter形式(extensionが一部キーのみ・metadataJson無し)
        val d = decode("""{"type":"book","title":"本","extension":{"isbn":"123","readStatus":"reading","authorsJson":"[\"A\"]"}}""")!!
        assertEquals("{}", d.entry.metadataJson)
        assertEquals("123", d.book?.isbn)
        assertEquals("reading", d.book?.readStatus)
        assertNull(d.book?.publisher)
    }

    @Test
    fun allElevenExtensionTypesAreRestored() {
        val cases = mapOf(
            "webpage" to """{"url":"https://www.example.com/a","domain":"example.com"}""",
            "book" to """{"authorsJson":"[]"}""",
            "video" to """{"platform":"youtube","videoId":"x"}""",
            "document" to """{"docType":"pdf","mimeType":"application/pdf","pageCount":3}""",
            "media" to """{"mediaType":"image","blobPath":"/p.jpg","mimeType":"image/jpeg"}""",
            "person" to """{"fullName":"織田信長","birthYear":1534}""",
            "org" to """{"officialName":"組織"}""",
            "place" to """{"placeName":"富山","latitude":36.7,"longitude":137.2}""",
            "event" to """{"eventName":"関ヶ原","startedAt":-11644473600000}""",
            "liked" to """{"platform":"x","originalId":"1","contentType":"post"}""",
            "ai_conv" to """{"model":"gemini","provider":"google","messagesJson":"[]"}"""
        )
        for ((type, ext) in cases) {
            val d = decode("""{"id":"i-$type","type":"$type","title":"T","extension":$ext}""")!!
            val restored = when (type) {
                "webpage" -> d.webpage; "book" -> d.book; "video" -> d.video; "document" -> d.document
                "media" -> d.media; "person" -> d.person; "org" -> d.org; "place" -> d.place
                "event" -> d.event; "liked" -> d.liked; "ai_conv" -> d.aiConv; else -> null
            }
            assertNotNull("拡張が復元されること: $type", restored)
        }
    }

    @Test
    fun webpageWithoutExtensionFallsBackToSourceUrl() {
        val d = decode("""{"type":"webpage","title":"T","sourceUrl":"https://www.foo.jp/x"}""")!!
        assertEquals("https://www.foo.jp/x", d.webpage?.url)
        assertEquals("foo.jp", d.webpage?.domain)
    }

    @Test
    fun mediaWithoutBlobPathIsNotRestored() {
        val d = decode("""{"type":"media","title":"T","extension":{"mediaType":"image"}}""")!!
        assertNull(d.media)
    }

    @Test
    fun missingTitleReturnsNull_andBadTypesAreTolerated() {
        assertNull(decode("""{"type":"thought"}"""))
        val d = decode("""{"type":"thought","title":"T","isFavorite":"yes","createdAt":"abc","tags":"notArray"}""")!!
        assertEquals(false, d.entry.isFavorite)
        assertEquals(999L, d.entry.createdAt)
        assertTrue(d.tags.isEmpty())
    }

    // ── ★wt56 付箋 ──

    @Test
    fun stickyNotes_roundtrip_keepIdPreservesEverything() {
        val d = decode(
            """
            {"id":"e1","type":"thought","title":"t","stickyNotes":[
              {"id":"s1","text":"あとで確認","color":"pink","source":"quiz","contextId":"q1",
               "isPinned":true,"isResolved":true,"resolvedAt":50,"sortOrder":2,
               "promotedEntryId":"pe","promotedTaskId":"pt","promotedCandidateId":"pc",
               "createdAt":10,"updatedAt":20},
              {"text":"色なし"}
            ]}
            """.trimIndent()
        )!!
        assertEquals(2, d.stickyNotes.size)
        val a = d.stickyNotes[0]
        assertEquals("s1", a.id)
        assertEquals("e1", a.entryId)
        assertEquals("pink", a.color)
        assertEquals("quiz", a.source)
        assertEquals("q1", a.contextId)
        assertTrue(a.isPinned); assertTrue(a.isResolved)
        assertEquals(50L, a.resolvedAt)
        assertEquals(2, a.sortOrder)
        assertEquals("pe", a.promotedEntryId); assertEquals("pt", a.promotedTaskId); assertEquals("pc", a.promotedCandidateId)
        assertEquals(10L, a.createdAt); assertEquals(20L, a.updatedAt)
        val b = d.stickyNotes[1]
        assertEquals("yellow", b.color)
        assertEquals("detail", b.source)
        assertEquals(999L, b.createdAt)
    }

    @Test
    fun stickyNotes_newId_reassignsEntryIdAndDropsPromotions() {
        val d = decode(
            """{"id":"e1","type":"thought","title":"t","stickyNotes":[{"id":"s1","text":"x","promotedEntryId":"pe"}]}""",
            keepId = false
        )!!
        val n = d.stickyNotes.single()
        assertEquals("NEW", n.entryId)
        assertTrue(n.id != "s1")
        assertNull(n.promotedEntryId)
    }

    @Test
    fun stickyNotes_blankOrBrokenItemsAreSkipped_notFatal() {
        val d = decode("""{"type":"thought","title":"t","stickyNotes":[{"text":"  "},"garbage",{"text":"ok"}]}""")!!
        assertEquals(listOf("ok"), d.stickyNotes.map { it.text })
    }

    @Test
    fun legacyExportWithoutStickyNotesKey_yieldsEmptyList() {
        val d = decode("""{"type":"thought","title":"t"}""")!!
        assertTrue(d.stickyNotes.isEmpty())
    }
}
