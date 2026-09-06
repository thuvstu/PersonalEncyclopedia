package com.thuvstu.personalencyclopedia.importer

import com.thuvstu.personalencyclopedia.db.entity.EntryEntity

/**
 * §12.5 Trie木による自動ハイパーリンク検出器。
 * 最長一致優先でテキスト内の他エントリータイトルを検出する。
 *
 * 設計上の注意（§5.5.3との整合性）:
 * - このクラスは「閲覧時のUI装飾」のみを担う
 * - connection テーブルへの書き込みは行わない
 * - ユーザーがタップして初めて手動接続を「提案」する形にする
 *   （接続候補承認フローと矛盾しない）
 *
 * ★wt46 表記揺れ対応(mismatch §1.2「完全一致Trie(表記揺れ・読み仮名非対応)」):
 * - 照合は1文字→1文字の正規化後に行う(全角英数記号→半角、半角カナ→全角、英字は小文字化)。
 *   1文字→1文字なので、返す範囲は元テキストの位置とそのまま一致する。
 * - タイトル以外の表層形(人物の別名/fullName、定義の読み、組織の正式名称、場所名、出来事名)も
 *   同じTrieに登録する。別名がどこかのタイトルと衝突した場合はタイトルが優先(別名は上書きしない)。
 * - 別名・揺れで一致した場合、[applyAsWikiLinks] は `[[正式タイトル|一致した文字列]]` を出力する。
 *   `RichContentView` の `[[title|alias]]` 記法により、表示は原文のまま・タップ時の解決は正式タイトルで行われる。
 * - 誤検出抑制: 英数字は単語境界(`Go`が`Google`に反応しない)、カタカナは同種文字の連続境界
 *   (`アイ`が`アイデア`に反応しない)でのみ一致する。ひらがなのみの表層形(読み)は前後の両側が
 *   ひらがなの位置(仮名の並びに埋もれた偶然の一致)では採用しない。文頭・漢字/句読点/括弧の直後
 *   (`院政（いんせい）`・`ていしもんだいについて`)は一致する。漢字を含む表層形は従来通り境界を問わない。
 *   かなのみの表層形は3文字以上のみ登録する([isUsableForm])。
 * - 語末の長音「ー」の有無(コンピュータ/コンピューター、サーバ/サーバー)は双方向に吸収する:
 *   「ー」で終わるカタカナ形は「ー」抜きも登録し、照合時は語末直後の「ー」を一致範囲に取り込む。
 * - 対象外(既知の限界): 異体字(澤/沢)、濁点付き半角カナ(ｸﾞ は2文字なので1:1正規化できない)、ひらがな⇄カタカナ。
 *
 * パフォーマンス:
 * - 1万エントリー × 平均10文字 = 10万ノードのTrie → メモリ数MB程度
 * - テキスト1000文字の走査 → O(n × maxTitleLen) ≒ 数ms
 * - エントリー追加/削除時は rebuild() で再構築（起動時+変更時のみ）
 */
class AutoLinker private constructor(
    titles: Sequence<Pair<String, String>>,   // (entryId, title)
    aliases: Sequence<Pair<String, String>>   // (entryId, 別名・読みなどの表層形)
) {
    /** 互換: 全カラムの EntryEntity から構築(削除済みは除外・別名なし) */
    constructor(entries: List<EntryEntity>) : this(
        entries.asSequence().filter { it.deletedAt == null }.map { it.id to it.title },
        emptySequence()
    )

    private class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var entryId: String? = null
        var canonicalTitle: String? = null
        var hiraganaOnly = false   // 登録された表層形がひらがなのみ(両側にひらがなが続く位置では一致させない)
    }

    private val root = TrieNode()

    /** entryId → 正式タイトル(trim済み)。別名登録時の逆引き用 */
    private val canonicalTitles = HashMap<String, String>()

    init {
        for ((id, rawTitle) in titles) {
            val title = rawTitle.trim()
            if (title.isEmpty()) continue
            canonicalTitles[id] = title
            // 1文字のタイトルは誤判定が多いためスキップ
            if (title.length < MIN_FORM_LENGTH) continue
            insert(title, id, title, overwrite = true)
            insertLongVowelVariant(title, id, title)
        }
        for ((id, rawAlias) in aliases) {
            val alias = rawAlias.trim()
            val canonical = canonicalTitles[id] ?: continue   // 削除済み・未知のentryの別名は無視
            if (alias == canonical || !isUsableForm(alias)) continue
            insert(alias, id, canonical, overwrite = false)   // タイトルと衝突したらタイトル優先
            insertLongVowelVariant(alias, id, canonical)
        }
    }

    /** 「…ター」のように語末が長音のカタカナ形は「…タ」も別名として登録(タイトル優先・上書きなし) */
    private fun insertLongVowelVariant(form: String, id: String, canonical: String) {
        if (form.length < 2 || form.last() != LONG_VOWEL || !isKatakana(form[form.length - 2])) return
        val stripped = form.dropLast(1)
        if (isUsableForm(stripped)) insert(stripped, id, canonical, overwrite = false)
    }

    private fun insert(form: String, id: String, canonical: String, overwrite: Boolean) {
        var current = root
        for (ch in form) {
            current = current.children.getOrPut(normalizeChar(ch)) { TrieNode() }
        }
        if (overwrite || current.entryId == null) {
            current.entryId = id
            current.canonicalTitle = canonical
            current.hiraganaOnly = form.all { isHiragana(it) }
        }
    }

    data class LinkMatch(
        val start: Int,
        val end: Int,               // exclusive
        val title: String,          // テキスト中で実際に一致した文字列(表示用)
        val entryId: String,
        val canonicalTitle: String  // ★wt46: リンク先entryの正式タイトル(タップ時の解決用)
    ) {
        val range: IntRange get() = start until end
    }

    /**
     * テキストの中からエントリータイトル(および別名・読み)の最長一致マッチを検出して返す。
     * 重複・重複範囲は排除（最長一致が優先）。範囲は元テキストの位置。
     */
    fun findMatches(text: String): List<LinkMatch> {
        val matches = mutableListOf<LinkMatch>()
        var i = 0
        while (i < text.length) {
            // 単語(英数字)・カタカナ語の途中からは一致を始めない
            if (i > 0 && boundaryBlocked(text[i - 1], text[i])) { i++; continue }

            var current = root
            var longestMatchId: String? = null
            var longestCanonical: String? = null
            var longestMatchLen = 0
            var j = i

            while (j < text.length) {
                val next = current.children[normalizeChar(text[j])] ?: break
                current = next
                if (current.entryId != null) {
                    // 単語・カタカナ語の途中で終わる一致は採用しない(より短い有効な一致が残る)
                    var end = j + 1
                    var ok = end >= text.length || !boundaryBlocked(text[j], text[end])
                    if (!ok && normalizeChar(text[end]) == LONG_VOWEL && isKatakana(text[j])) {
                        // 語末直後の「ー」は一致範囲に取り込む(コンピュータ → コンピューター)
                        end++
                        ok = end >= text.length || !boundaryBlocked(text[end - 1], text[end])
                    }
                    if (ok && current.hiraganaOnly && embeddedInHiragana(text, i, end)) ok = false
                    if (ok) {
                        longestMatchId = current.entryId
                        longestCanonical = current.canonicalTitle
                        longestMatchLen = end - i
                    }
                }
                j++
            }

            if (longestMatchId != null && longestMatchLen > 0) {
                val matchedTitle = text.substring(i, i + longestMatchLen)
                matches.add(
                    LinkMatch(
                        start = i,
                        end = i + longestMatchLen,
                        title = matchedTitle,
                        entryId = longestMatchId,
                        canonicalTitle = longestCanonical ?: matchedTitle
                    )
                )
                i += longestMatchLen  // 最長一致分をスキップ
            } else {
                i++
            }
        }
        return matches
    }

    /**
     * ★P2-A: 検出マッチを `[[title]]` 記法でテキストに埋め込んだ表示用文字列を返す。
     * RichContentView の既存パイプラインが `[[wiki-link]]` として描画・プレビューするため、
     * 描画側の改修なしで自動リンクが発動する。DBへの書き込みは行わない（§5.5.3承認制と整合）。
     * - 自分自身のタイトルは除外する（自己プレビュー防止）
     * - 既存の `[[...]]` 領域内のマッチは二重化しない
     * - 後方から置換するためインデックスずれが起きない
     * - ★wt46: 一致文字列が正式タイトルと異なる(別名・全半角・大小文字)場合は
     *   `[[正式タイトル|一致文字列]]` にして、表示は原文のまま・解決は正式タイトルで行う
     * 純粋関数（JVMテスト可能）。
     */
    fun applyAsWikiLinks(text: String, selfEntryId: String? = null): String {
        if (text.isBlank()) return text
        val matches = findMatches(text)
            .filter { selfEntryId == null || it.entryId != selfEntryId }
        if (matches.isEmpty()) return text
        val wikiRanges = Regex("""\[\[[^\]]*]]""").findAll(text)
            .map { it.range }.toList()
        val sb = StringBuilder(text)
        for (m in matches.sortedByDescending { it.start }) {
            // 既存 [[...]] 領域 ([first, last+1)) の内側はスキップ
            val inside = wikiRanges.any { r -> m.start >= r.first && m.end <= r.last + 1 }
            if (inside) continue
            val markup = wikiLinkMarkup(m.canonicalTitle, m.title) ?: continue
            sb.replace(m.start, m.end, markup)
        }
        return sb.toString()
    }

    companion object {
        /** 2文字未満の表層形は登録しない(従来通り) */
        const val MIN_FORM_LENGTH = 2

        /** かなのみの別名・読みは3文字以上で登録(「ふく」「アイ」等の2文字は誤検出が多い) */
        const val MIN_KANA_FORM_LENGTH = 3

        // U+FF66(ｦ)〜U+FF9D(ﾝ) → 全角カタカナ。ﾞ(U+FF9E)/ﾟ(U+FF9F)は合成で文字数が変わるため対象外
        private const val HALFWIDTH_KATAKANA =
            "ヲァィゥェォャュョッーアイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン"

        private const val LONG_VOWEL = 'ー'   // U+30FC

        private const val CLASS_NONE = 0
        private const val CLASS_ALNUM = 1
        private const val CLASS_KATAKANA = 2

        /**
         * 1文字→1文字の照合用正規化(範囲がずれないことが前提)。
         * 全角英数記号→半角、全角スペース→半角、半角カナ→全角カナ、英字→小文字。
         */
        fun normalizeChar(c: Char): Char {
            val code = c.code
            val mapped = when {
                code in 0xFF01..0xFF5E -> (code - 0xFEE0).toChar()
                code == 0x3000 -> ' '
                code in 0xFF66..0xFF9D -> HALFWIDTH_KATAKANA[code - 0xFF66]
                else -> c
            }
            return mapped.lowercaseChar()
        }

        /** 境界判定用の文字種(正規化後で判定)。漢字・ひらがな・記号は CLASS_NONE(境界を問わない) */
        private fun boundaryClass(c: Char): Int {
            val n = normalizeChar(c)
            val code = n.code
            return when {
                n in 'a'..'z' || n in '0'..'9' -> CLASS_ALNUM
                code in 0x30A1..0x30FA || code in 0x30FC..0x30FE -> CLASS_KATAKANA  // 「・」(U+30FB)は区切り
                else -> CLASS_NONE
            }
        }

        /** a→b の間で一致を開始/終了してはいけないか(同じ文字種の単語の途中か) */
        fun boundaryBlocked(a: Char, b: Char): Boolean {
            val ca = boundaryClass(a)
            return ca != CLASS_NONE && ca == boundaryClass(b)
        }

        private fun isHiragana(c: Char): Boolean = c.code in 0x3041..0x309F
        private fun isKatakana(c: Char): Boolean {
            val code = c.code
            return code in 0x30A0..0x30FF || code in 0xFF66..0xFF9F
        }

        /** [start, end) の直前も直後もひらがなか(ひらがなのみの表層形は、この位置では採用しない) */
        private fun embeddedInHiragana(text: String, start: Int, end: Int): Boolean =
            start > 0 && isHiragana(text[start - 1]) && end < text.length && isHiragana(text[end])

        /** 別名・読みとして登録してよい表層形か(長さ規則) */
        fun isUsableForm(form: String): Boolean {
            if (form.length < MIN_FORM_LENGTH) return false
            val kanaOnly = form.all { isHiragana(it) || isKatakana(it) }
            return !kanaOnly || form.length >= MIN_KANA_FORM_LENGTH
        }

        /**
         * `[[title]]` / `[[title|display]]` の記法を生成。RichContentView の正規表現
         * `\[\[([^\]|]+)(?:\|([^\]]+))?]]` で解釈できない文字を含む場合は null(リンク化しない)。
         */
        fun wikiLinkMarkup(canonicalTitle: String, display: String): String? {
            if (canonicalTitle.any { it == '[' || it == ']' || it == '|' || it == '\n' }) return null
            if (display.any { it == '[' || it == ']' || it == '|' || it == '\n' }) return null
            return if (display == canonicalTitle) "[[$display]]" else "[[$canonicalTitle|$display]]"
        }

        /**
         * エントリーリストからAutoLinkerを構築。
         * 起動時・エントリー変更時に呼び出す。
         */
        fun build(entries: List<EntryEntity>): AutoLinker = AutoLinker(entries)

        /**
         * ★wt44: (id, title) の軽量射影から構築。`EntryDao.getAllTitles()` 用。
         * ★wt46: `aliases` に (id, 別名/読み) を渡すと表記揺れも同じentryに解決する。
         */
        fun fromTitles(
            titles: List<Pair<String, String>>,
            aliases: List<Pair<String, String>> = emptyList()
        ): AutoLinker = AutoLinker(titles.asSequence(), aliases.asSequence())
    }
}
