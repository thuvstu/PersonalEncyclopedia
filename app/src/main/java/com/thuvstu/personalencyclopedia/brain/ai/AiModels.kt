package com.thuvstu.personalencyclopedia.brain.ai

/**
 * 利用可能なAIモデルのレジストリ（§4.5）。
 * supportsGrounding/Search は無料枠のツール対応（既定値・設定で上書き可）。
 */
data class GeminiModelDef(
    val id: String,
    val label: String,
    val supportsJson: Boolean,
    val supportsGrounding: Boolean,
    val tier: String
)

object AiModels {
    val GEMINI_CHAT_MODELS = listOf(
        GeminiModelDef("gemini-3.6-flash",      "Gemini 3.6 Flash",      true, true,  "無料◎"),
        GeminiModelDef("gemini-3.5-flash",      "Gemini 3.5 Flash",      true, true,  "無料◎"),
        GeminiModelDef("gemini-3-flash",        "Gemini 3 Flash",        true, true,  "無料○"),
        GeminiModelDef("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite", true, false, "無料◎(軽量)"),
        GeminiModelDef("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", true, false, "無料◎(軽量)"),
    )
    const val GEMINI_EMBEDDING = "gemini-embedding-2-preview"

    fun chatById(id: String): GeminiModelDef =
        GEMINI_CHAT_MODELS.firstOrNull { it.id == id } ?: GEMINI_CHAT_MODELS.first()
}