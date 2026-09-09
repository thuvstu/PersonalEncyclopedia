package com.thuvstu.personalencyclopedia.brain.search

import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ★wt56: 付箋本文が検索文書に連結されること・本文を押し出さないこと */
class EmbeddingTextBuilderStickyTest {

    private val entry = EntryEntity(id = "e", type = "thought", title = "タイトル", content = "本文")

    @Test
    fun noStickyNotes_isUnchanged() {
        assertEquals("タイトル\n本文", EmbeddingTextBuilder.build(entry, null))
        assertEquals("タイトル\n本文", EmbeddingTextBuilder.build(entry, null, emptyList()))
    }

    @Test
    fun stickyNotesAreAppended_andBlankOnesDropped() {
        val out = EmbeddingTextBuilder.build(entry, null, listOf("  ", "あとで確認する", "疑問: なぜ？"))
        assertEquals("タイトル\n本文\nあとで確認する\n疑問: なぜ？", out)
    }

    @Test
    fun longBodyStillLeavesRoomForStickyNotes() {
        val longEntry = entry.copy(content = "あ".repeat(5000))
        val out = EmbeddingTextBuilder.build(longEntry, null, listOf("付箋キーワード"))
        assertTrue(out.length <= 2000)
        assertTrue("本文が1600字で切られ付箋が残ること", out.endsWith("付箋キーワード"))
    }

    @Test
    fun stickyNotesAreCappedAt400Chars() {
        val out = EmbeddingTextBuilder.build(entry, null, listOf("い".repeat(1000)))
        assertTrue(out.length <= 2000)
        val notePart = out.removePrefix("タイトル\n本文\n")
        assertEquals(400, notePart.length)
        assertFalse(notePart.contains("タイトル"))
    }
}
