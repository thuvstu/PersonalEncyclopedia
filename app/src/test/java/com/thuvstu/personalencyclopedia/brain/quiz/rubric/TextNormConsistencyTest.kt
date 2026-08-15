package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import com.thuvstu.personalencyclopedia.brain.quiz.EraConverter
import com.thuvstu.personalencyclopedia.brain.quiz.MultiStageGrader
import com.thuvstu.personalencyclopedia.db.dao.EraMasterDao
import com.thuvstu.personalencyclopedia.db.entity.EraMasterEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 新採点システムの TextNorm が既存 MultiStageGrader と同一仕様であることの突き合わせテスト。
 * (設計方針: 既存コードを唯一の正とする。仕様乖離はここで検出する)
 */
class TextNormConsistencyTest {

    private val fakeDao = object : EraMasterDao {
        override suspend fun getAll(): List<EraMasterEntity> = emptyList()
        override suspend fun getByName(name: String): EraMasterEntity? = null
    }
    private lateinit var grader: MultiStageGrader

    @Before
    fun setup() {
        grader = MultiStageGrader(EraConverter(fakeDao))
    }

    @Test
    fun `normalize matches legacy grader normalize`() {
        val cases = listOf(
            "日本は プレート境界 に位置する",
            "１０００年",
            "ＡＢＣ",
            "（ ）",
            "「引用」",
            "プレートの境界、地震。"
        )
        cases.forEach { input ->
            assertEquals(
                "normalize($input)",
                grader.normalize(input),
                TextNorm.normalize(input)
            )
        }
    }

    @Test
    fun `similarity matches legacy grader similarity`() {
        val pairs = listOf(
            "プレート境界" to "プレート境界",
            "プレート境界" to "プレート境",
            "地球の内部" to "地球内部",
            "水素" to "ヘリウム"
        )
        pairs.forEach { (a, b) ->
            assertEquals(
                "similarity($a,$b)",
                grader.similarity(a, b),
                TextNorm.similarity(a, b),
                0.001f
            )
        }
    }

    @Test
    fun `bigram overlap detects shared content`() {
        val overlap = TextNorm.bigramOverlap(
            "日本はプレート境界に位置する",
            "日本はプレート境界に位置しない"
        )
        assert(overlap >= 0.7f) { "否定だけの差は高い重なり率になるべき: $overlap" }
    }
}
