package com.thuvstu.personalencyclopedia.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/** ★wt56: StickyNoteRepository の純粋関数（DB不要部分） */
class StickyNoteRepositoryPureTest {

    @Test
    fun titleFrom_usesFirstNonBlankLine_andTruncatesAt40() {
        assertEquals("付箋", StickyNoteRepository.titleFrom("   \n  \n"))
        assertEquals("二行目", StickyNoteRepository.titleFrom("\n  二行目\n三行目"))
        val long = "あ".repeat(60)
        val t = StickyNoteRepository.titleFrom(long)
        assertEquals(40, t.length)
        assertEquals("あ".repeat(39) + "…", t)
        assertEquals("あ".repeat(40), StickyNoteRepository.titleFrom("あ".repeat(40)))
    }

    @Test
    fun extractWikiLinks_handlesAliasesDuplicatesAndBlank() {
        val links = StickyNoteRepository.extractWikiLinks("これは[[福澤諭吉]]と[[慶應義塾|慶應]]、また[[福澤諭吉]]。[[ ]]は無視")
        assertEquals(listOf("福澤諭吉", "慶應義塾"), links)
        assertEquals(emptyList<String>(), StickyNoteRepository.extractWikiLinks("リンクなし"))
    }
}
