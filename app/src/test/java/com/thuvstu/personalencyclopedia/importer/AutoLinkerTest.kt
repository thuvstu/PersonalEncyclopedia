package com.thuvstu.personalencyclopedia.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ★wt46 (mismatch §1.2 残課題「完全一致Trie(表記揺れ・読み仮名非対応)」):
 * 別名・読み・全半角・大小文字の揺れが同じentryに解決し、`[[正式タイトル|原文]]` で埋め込まれること。
 * 純粋関数のためDB不要。
 */
class AutoLinkerTest {

    private val linker = AutoLinker.fromTitles(
        titles = listOf(
            "e-fukuzawa" to "福澤諭吉",
            "e-halting" to "停止問題",
            "e-turing" to "チューリング機械",
            "e-go" to "Go",
            "e-google" to "Google",
            "e-ai" to "アイ",
            "e-idea" to "アイデア",
            "e-smith" to "アダム・スミス",
            "e-computer" to "コンピュータ",
            "e-server" to "サーバー",
        ),
        aliases = listOf(
            "e-fukuzawa" to "ふくざわゆきち",
            "e-fukuzawa" to "福沢諭吉",
            "e-halting" to "ていしもんだい",
            "e-smith" to "Adam Smith",
            "e-smith" to "スミス",
            "e-ghost" to "幽霊別名",           // タイトルの無いentryの別名 → 無視
            "e-go" to "ふく",                   // 短いかな → 未登録
            "e-turing" to "停止問題",           // 他entryのタイトルと衝突 → タイトル優先
        )
    )

    @Test
    fun exactTitle_stillMatches_andKeepsPlainMarkup() {
        val out = linker.applyAsWikiLinks("福澤諭吉は停止問題を知らない")
        assertEquals("[[福澤諭吉]]は[[停止問題]]を知らない", out)
    }

    @Test
    fun alias_resolvesToCanonicalTitle_withDisplayPreserved() {
        val out = linker.applyAsWikiLinks("福沢諭吉の『学問のすゝめ』")
        assertEquals("[[福澤諭吉|福沢諭吉]]の『学問のすゝめ』", out)
        val m = linker.findMatches("福沢諭吉").single()
        assertEquals("e-fukuzawa", m.entryId)
        assertEquals("福澤諭吉", m.canonicalTitle)
        assertEquals("福沢諭吉", m.title)
    }

    @Test
    fun reading_isRegisteredAsAlias() {
        val ms = linker.findMatches("ていしもんだいは決定不能")
        assertEquals(1, ms.size)
        assertEquals("e-halting", ms[0].entryId)
        assertEquals(0, ms[0].start)
        assertEquals("ていしもんだい".length, ms[0].end)
    }

    @Test
    fun hiraganaReading_isNotMatchedWhenEmbeddedInHiragana() {
        // 括弧内・文頭・漢字の直後は一致する(片側でも非ひらがなならOK)
        assertEquals(listOf("e-halting", "e-halting"), linker.findMatches("停止問題（ていしもんだい）とは").map { it.entryId })
        assertEquals(listOf("e-fukuzawa"), linker.findMatches("「ふくざわゆきち」").map { it.entryId })
        assertEquals(listOf("e-halting"), linker.findMatches("ていしもんだいについて調べる").map { it.entryId })
        assertEquals(listOf("e-halting"), linker.findMatches("→ていしもんだいが本質").map { it.entryId })
        // 両側がひらがな(仮名の並びに埋もれた偶然の一致)は拾わない
        assertTrue(linker.findMatches("そのていしもんだいが").isEmpty())
        // 漢字を含む表層形は従来通り境界を問わない
        assertEquals(listOf("e-halting"), linker.findMatches("この停止問題が").map { it.entryId })
        assertEquals(listOf("e-fukuzawa"), linker.findMatches("その福沢諭吉が").map { it.entryId })
    }

    @Test
    fun fullWidthAndCase_areNormalized_butRangesStayOriginal() {
        val text = "ＧＯＯＧＬＥ と adam smith"
        val ms = linker.findMatches(text)
        assertEquals(listOf("e-google", "e-smith"), ms.map { it.entryId })
        assertEquals("ＧＯＯＧＬＥ", text.substring(ms[0].start, ms[0].end))
        assertEquals("adam smith", text.substring(ms[1].start, ms[1].end))
        assertEquals("[[Google|ＧＯＯＧＬＥ]] と [[アダム・スミス|adam smith]]", linker.applyAsWikiLinks(text))
    }

    @Test
    fun halfWidthKatakana_matchesFullWidthTitle() {
        val text = "ｽﾐｽの分業論"
        val ms = linker.findMatches(text)
        assertEquals(1, ms.size)
        assertEquals("e-smith", ms[0].entryId)
        assertEquals("ｽﾐｽ", ms[0].title)
    }

    @Test
    fun alnumWordBoundary_preventsPartialWordMatch() {
        // "Go" は "Google"/"Golang" の内部・"ago" の末尾で発火しない
        assertEquals(listOf("e-google"), linker.findMatches("Google").map { it.entryId })
        assertTrue(linker.findMatches("Golang ago").isEmpty())
        assertEquals(listOf("e-go"), linker.findMatches("write Go code").map { it.entryId })
        assertEquals(listOf("e-go"), linker.findMatches("Goで書く").map { it.entryId })
    }

    @Test
    fun katakanaBoundary_preventsPartialKatakanaMatch() {
        assertEquals(listOf("e-idea"), linker.findMatches("アイデアを出す").map { it.entryId })
        assertTrue(linker.findMatches("ハワイアイランド").isEmpty())
        assertEquals(listOf("e-ai"), linker.findMatches("アイと呼ぶ").map { it.entryId })
        // 中黒は区切り: 別名「スミス」は「アダム・スミス」の後半では発火せず、全体がタイトル一致
        val ms = linker.findMatches("アダム・スミスの")
        assertEquals(listOf("e-smith"), ms.map { it.entryId })
        assertEquals("アダム・スミス", ms[0].title)
    }

    @Test
    fun trailingLongVowel_isAbsorbedBothWays() {
        // タイトル「コンピュータ」 ← 本文「コンピューター」(語末の「ー」を範囲に含めて表示は原文のまま)
        var ms = linker.findMatches("コンピューターを使う")
        assertEquals(listOf("e-computer"), ms.map { it.entryId })
        assertEquals("コンピューター", ms[0].title)
        assertEquals("[[コンピュータ|コンピューター]]を使う", linker.applyAsWikiLinks("コンピューターを使う"))
        // タイトル「サーバー」 ← 本文「サーバ」(「ー」抜きの形を別名として登録)
        ms = linker.findMatches("サーバを再起動")
        assertEquals(listOf("e-server"), ms.map { it.entryId })
        assertEquals("[[サーバー|サーバ]]を再起動", linker.applyAsWikiLinks("サーバを再起動"))
        assertEquals("[[サーバー]]", linker.applyAsWikiLinks("サーバー"))
        // カタカナ語の途中(サーバーレス / コンピューターサイエンス)では発火しない
        assertTrue(linker.findMatches("サーバーレス").isEmpty())
        assertTrue(linker.findMatches("コンピューターサイエンス").isEmpty())
    }

    @Test
    fun titleWinsOverConflictingAlias_andUnknownAliasIgnored() {
        assertEquals("e-halting", linker.findMatches("停止問題").single().entryId)
        assertTrue(linker.findMatches("幽霊別名").isEmpty())
        assertTrue(linker.findMatches("ふく").isEmpty())
    }

    @Test
    fun selfEntry_isExcluded_andExistingWikiLinksUntouched() {
        assertEquals("福沢諭吉の本", linker.applyAsWikiLinks("福沢諭吉の本", selfEntryId = "e-fukuzawa"))
        assertEquals("[[福澤諭吉]]と[[停止問題]]", linker.applyAsWikiLinks("[[福澤諭吉]]と[[停止問題]]"))
        assertEquals("[[福澤諭吉|福沢先生]]", linker.applyAsWikiLinks("[[福澤諭吉|福沢先生]]"))
    }

    @Test
    fun isUsableForm_lengthRules() {
        assertFalse(AutoLinker.isUsableForm("あ"))
        assertFalse(AutoLinker.isUsableForm("ふく"))             // かな2文字は不可
        assertFalse(AutoLinker.isUsableForm("アイ"))
        assertTrue(AutoLinker.isUsableForm("ところ"))            // かな3文字から可
        assertTrue(AutoLinker.isUsableForm("スミス"))
        assertTrue(AutoLinker.isUsableForm("Go"))                // かな以外は2文字から可
        assertTrue(AutoLinker.isUsableForm("福沢"))
    }

    @Test
    fun wikiLinkMarkup_refusesUnparsableCharacters() {
        assertEquals("[[A]]", AutoLinker.wikiLinkMarkup("A", "A"))
        assertEquals("[[A|a]]", AutoLinker.wikiLinkMarkup("A", "a"))
        assertNull(AutoLinker.wikiLinkMarkup("A|B", "x"))
        assertNull(AutoLinker.wikiLinkMarkup("A", "x]"))
    }

    @Test
    fun expandAliasForms_splitsJsonArrays() {
        val out = AutoLinkerProvider.expandAliasForms(
            listOf("p1" to """["ふくざわゆきち","福沢諭吉"]""", "p1" to "福澤 諭吉", "p2" to "[]", "p3" to " ", "p4" to "[broken")
        )
        assertEquals(
            listOf("p1" to "ふくざわゆきち", "p1" to "福沢諭吉", "p1" to "福澤 諭吉"),
            out
        )
    }

    @Test
    fun legacyEntityConstructor_stillWorks() {
        val entries = listOf(
            com.thuvstu.personalencyclopedia.db.entity.EntryEntity(id = "a", type = "thought", title = "再帰"),
            com.thuvstu.personalencyclopedia.db.entity.EntryEntity(id = "b", type = "thought", title = "削除済", deletedAt = 1L),
        )
        val l = AutoLinker.build(entries)
        assertEquals("[[再帰]]と削除済", l.applyAsWikiLinks("再帰と削除済"))
    }
}
