package com.thuvstu.personalencyclopedia.brain.quiz

/**
 * 出題形式の単一カタログ。設定・編集・一覧・プレイ・採点はここを見る。
 * 新しい形式は1行足して play/grade を実装すればよい。
 */
enum class QuizPlayKind { WRITTEN, CHOICE, SORT, CLOZE, TF, MULTI, MATCH }

data class QuizFormatDef(
    val id: String,
    val label: String,
    val play: QuizPlayKind,
    val needsChoices: Boolean = false,
    val sequenceGrade: Boolean = false,
    val setGrade: Boolean = false,
    val exactGrade: Boolean = false
)

object QuizFormats {
    val ALL: List<QuizFormatDef> = listOf(
        QuizFormatDef("qa", "記述式", QuizPlayKind.WRITTEN),
        QuizFormatDef("mcq", "選択式", QuizPlayKind.CHOICE, needsChoices = true, exactGrade = true),
        QuizFormatDef("fill_blank", "穴埋め", QuizPlayKind.CLOZE),
        QuizFormatDef("sort", "並べ替え", QuizPlayKind.SORT, needsChoices = true, sequenceGrade = true),
        QuizFormatDef("cloze", "複数穴埋め", QuizPlayKind.CLOZE, sequenceGrade = true),
        QuizFormatDef("tf", "正誤", QuizPlayKind.TF, exactGrade = true),
        QuizFormatDef("multi", "複数選択", QuizPlayKind.MULTI, needsChoices = true, setGrade = true),
        QuizFormatDef("match", "対応づけ", QuizPlayKind.MATCH, needsChoices = true, setGrade = true)
    )

    val IDS: Set<String> = ALL.map { it.id }.toSet()

    fun of(id: String): QuizFormatDef? = ALL.find { it.id == id }
    fun label(id: String): String = of(id)?.label ?: id
    fun chips(): List<Pair<String, String>> = ALL.map { it.id to it.label }
}
