package com.thuvstu.personalencyclopedia.importer

object WikiLinkParser {
    data class ParsedNote(
        val title: String,
        val content: String,
        val wikiLinks: List<String>
    )

    /**
     * Markdownテキストから [[wiki-link]] を抽出する。
     * Notion/Obsidian 双方の [[Title]] や [[Title|Alias]] 形式に対応。
     */
    fun parse(title: String, content: String): ParsedNote {
        // [[...]] の中身を抽出。 | があればその前をタイトルとして扱う（Notion/Obsidian共通）
        val wikiLinkRegex = Regex("""\[\[(.*?)(?:\|.*?)?]]""")
        val links = wikiLinkRegex.findAll(content)
            .map { it.groupValues[1].trim() }
            .distinct()
            .toList()

        return ParsedNote(title = title, content = content, wikiLinks = links)
    }
}