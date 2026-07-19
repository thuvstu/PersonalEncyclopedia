package com.thuvstu.personalencyclopedia.ui.theme

import androidx.compose.ui.graphics.Color

// Entry Type Colors (§11.2)
val TypeWebpage    = Color(0xFF3B82F6)
val TypeThought    = Color(0xFF8B5CF6)
val TypeBook       = Color(0xFFF59E0B)
val TypeVideo      = Color(0xFFEF4444)
val TypeDocument   = Color(0xFF64748B)
val TypeDefinition = Color(0xFF10B981)
val TypePerson     = Color(0xFFEC4899)
val TypePlace      = Color(0xFF14B8A6)
val TypeEvent      = Color(0xFFF97316)
val TypeAiConv     = Color(0xFF6366F1)
val TypeLiked      = Color(0xFFF43F5E)

fun entryTypeColor(type: String): Color = when (type) {
    "webpage"    -> TypeWebpage
    "thought"    -> TypeThought
    "book"       -> TypeBook
    "video"      -> TypeVideo
    "document"   -> TypeDocument
    "definition" -> TypeDefinition
    "person"     -> TypePerson
    "place"      -> TypePlace
    "event"      -> TypeEvent
    "ai_conv"    -> TypeAiConv
    "liked"      -> TypeLiked
    else         -> Color(0xFF94A3B8)
}

fun entryTypeIcon(type: String): String = when (type) {
    "thought"    -> "💭"
    "definition" -> "📖"
    "webpage"    -> "🌐"
    "book"       -> "📚"
    "video"      -> "🎬"
    "document"   -> "📄"
    "media"      -> "🖼️"
    "person"     -> "👤"
    "org"        -> "🏢"
    "place"      -> "📍"
    "event"      -> "📅"
    "liked"      -> "❤️"
    "ai_conv"    -> "🤖"
    else         -> "📝"
}

fun entryTypeLabelJa(type: String): String = when (type) {
    "thought"    -> "メモ"
    "definition" -> "単語帳"
    "webpage"    -> "Webページ"
    "book"       -> "本"
    "video"      -> "動画"
    "document"   -> "ドキュメント"
    "media"      -> "メディア"
    "person"     -> "人物"
    "org"        -> "組織"
    "place"      -> "場所"
    "event"      -> "出来事"
    "liked"      -> "いいね"
    "ai_conv"    -> "AI会話"
    else         -> type
}