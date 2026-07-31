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
 * パフォーマンス:
 * - 1万エントリー × 平均10文字 = 10万ノードのTrie → メモリ数MB程度
 * - テキスト1000文字の走査 → O(n × maxTitleLen) ≒ 数ms
 * - エントリー追加/削除時は rebuild() で再構築（起動時+変更時のみ）
 */
class AutoLinker(entries: List<EntryEntity>) {

    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var entryId: String? = null
        var titleLength: Int = 0
    }

    private val root = TrieNode()

    init {
        for (entry in entries) {
            val title = entry.title.trim()
            // 1文字のタイトルは誤判定が多いためスキップ
            if (title.length < 2) continue
            // 削除済みエントリーは除外
            if (entry.deletedAt != null) continue
            insert(title, entry.id)
        }
    }

    private fun insert(title: String, id: String) {
        var current = root
        for (ch in title) {
            current = current.children.getOrPut(ch) { TrieNode() }
        }
        current.entryId = id
        current.titleLength = title.length
    }

    data class LinkMatch(
        val start: Int,
        val end: Int,          // exclusive
        val title: String,
        val entryId: String
    ) {
        val range: IntRange get() = start until end
    }

    /**
     * テキストの中からエントリータイトルの最長一致マッチを検出して返す。
     * 重複・重複範囲は排除（最長一致が優先）。
     */
    fun findMatches(text: String): List<LinkMatch> {
        val matches = mutableListOf<LinkMatch>()
        var i = 0
        while (i < text.length) {
            var current = root
            var longestMatchId: String? = null
            var longestMatchLen = 0
            var j = i

            while (j < text.length) {
                val ch = text[j]
                val next = current.children[ch] ?: break
                current = next
                if (current.entryId != null) {
                    longestMatchId = current.entryId
                    longestMatchLen = j - i + 1
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
                        entryId = longestMatchId
                    )
                )
                i += longestMatchLen  // 最長一致分をスキップ
            } else {
                i++
            }
        }
        return matches
    }

    companion object {
        /**
         * エントリーリストからAutoLinkerを構築。
         * 起動時・エントリー変更時に呼び出す。
         */
        fun build(entries: List<EntryEntity>): AutoLinker = AutoLinker(entries)
    }
}