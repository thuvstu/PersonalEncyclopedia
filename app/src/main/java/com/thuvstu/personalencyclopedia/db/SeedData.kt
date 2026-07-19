package com.thuvstu.personalencyclopedia.db

import com.thuvstu.personalencyclopedia.db.entity.EntryTypeEntity

object SeedData {
    val entryTypes = listOf(
        EntryTypeEntity("thought",    "メモ・思考",     "💭", "#8B5CF6", sortOrder = 0),
        EntryTypeEntity("definition", "用語・単語帳",   "📖", "#10B981", sortOrder = 1),
        EntryTypeEntity("webpage",    "Webページ",     "🌐", "#3B82F6", sortOrder = 2),
        EntryTypeEntity("book",       "本",            "📚", "#F59E0B", sortOrder = 3),
        EntryTypeEntity("video",      "動画",          "🎬", "#EF4444", sortOrder = 4),
        EntryTypeEntity("document",   "ドキュメント",   "📄", "#64748B", sortOrder = 5),
        EntryTypeEntity("media",      "メディア",       "🖼️", "#06B6D4", sortOrder = 6),
        EntryTypeEntity("person",     "人物",          "👤", "#EC4899", sortOrder = 7),
        EntryTypeEntity("org",        "組織",          "🏢", "#A855F7", sortOrder = 8),
        EntryTypeEntity("place",      "場所",          "📍", "#14B8A6", sortOrder = 9),
        EntryTypeEntity("event",      "出来事",        "📅", "#F97316", sortOrder = 10),
        EntryTypeEntity("liked",      "いいね済み",     "❤️", "#F43F5E", sortOrder = 11),
        EntryTypeEntity("ai_conv",    "AI会話",        "🤖", "#6366F1", sortOrder = 12),
    )
}