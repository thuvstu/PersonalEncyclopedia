package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import com.thuvstu.personalencyclopedia.brain.quiz.EraConverter
import com.thuvstu.personalencyclopedia.brain.quiz.MultiStageGrader
import com.thuvstu.personalencyclopedia.db.dao.EraMasterDao
import com.thuvstu.personalencyclopedia.db.entity.EraMasterEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RubricFeatureExtractorTest {

    private val eras = listOf(
        EraMasterEntity("慶長", 1596, 1615, 5),
        EraMasterEntity("令和", 2019, null, 45)
    )
    private val fakeDao = object : EraMasterDao {
        override suspend fun getAll(): List<EraMasterEntity> = eras
        override suspend fun getByName(name: String): EraMasterEntity? =
            eras.firstOrNull { it.name == name }
    }

    private lateinit var extractor: RubricFeatureExtractor

    private fun bundleOf(vararg items: RubricItem, modelAnswers: List<String> = emptyList()): RubricParser.RubricBundle =
        RubricParser.RubricBundle(items.toList(), modelAnswers.ifEmpty { listOf("模範") })

    @Before
    fun setup() {
        extractor = RubricFeatureExtractor(
            FakeEmbeddingProvider(),
            NumericUnitVerifier(MultiStageGrader(EraConverter(fakeDao)))
        )
    }

    @Test
    fun `keyword hit is high confidence`() = runBlocking {
        val bundle = bundleOf(RubricItem("k1", RubricKind.KEYWORD, "必須語", "プレート境界"))
        val f = extractor.extract("日本はプレート境界に位置する", bundle).single()
        assertEquals(1.0f, f.score, 0.001f)
        assertEquals(0.95f, f.confidence, 0.001f)
        assertTrue(f.signals.first() is Signal.KeywordHit)
    }

    @Test
    fun `keyword missing scores zero`() = runBlocking {
        val bundle = bundleOf(RubricItem("k1", RubricKind.KEYWORD, "必須語", "沈み込み"))
        val f = extractor.extract("日本はプレート境界に位置する", bundle).single()
        assertEquals(0f, f.score, 0.001f)
        assertTrue(f.signals.first() is Signal.KeywordMissing)
    }

    @Test
    fun `numeric unit equivalence is highest confidence`() = runBlocking {
        val bundle = bundleOf(RubricItem("n1", RubricKind.NUMERIC_UNIT, "単位変換", "72 km/h"))
        val f = extractor.extract("20 m/s", bundle).single()
        assertEquals(1.0f, f.score, 0.001f)
        assertEquals(0.98f, f.confidence, 0.001f)
        assertTrue(f.signals.first() is Signal.NumericEquivalent)
    }

    @Test
    fun `numeric undeterminable is low confidence`() = runBlocking {
        val bundle = bundleOf(RubricItem("n1", RubricKind.NUMERIC_UNIT, "単位", "関ヶ原の戦い"))
        val f = extractor.extract("蒸気機関車が発明された", bundle).single()
        assertEquals(0.5f, f.score, 0.001f)
        assertEquals(0.20f, f.confidence, 0.001f)
        assertEquals(Signal.Undeterminable, f.signals.first())
    }

    @Test
    fun `polarity reversal emits contradiction`() = runBlocking {
        val bundle = bundleOf(RubricItem("p1", RubricKind.POLARITY, "極性", "POSITIVE"))
        val f = extractor.extract("日本はプレート境界に位置しない。", bundle).single()
        assertEquals(0f, f.score, 0.001f)
        assertTrue(f.signals.any { it is Signal.PolarityReversed })
        assertTrue(f.signals.any { it is Signal.ContradictionDetected })
    }

    @Test
    fun `polarity match is high confidence`() = runBlocking {
        val bundle = bundleOf(RubricItem("p1", RubricKind.POLARITY, "極性", "POSITIVE"))
        val f = extractor.extract("日本はプレート境界に位置する。", bundle).single()
        assertEquals(1.0f, f.score, 0.001f)
        assertTrue(f.signals.first() is Signal.PolarityMatched)
    }

    @Test
    fun `relation reversal emits contradiction`() = runBlocking {
        val bundle = bundleOf(RubricItem("r1", RubricKind.RELATION, "因果", "工業化が環境汚染の原因"))
        val f = extractor.extract("環境汚染が工業化の原因", bundle).single()
        assertEquals(0f, f.score, 0.001f)
        assertTrue(f.signals.any { it is Signal.RelationReversed })
        assertTrue(f.signals.any { it is Signal.ContradictionDetected })
    }

    @Test
    fun `concept detected via embedding when similar`() = runBlocking {
        val bundle = bundleOf(
            RubricItem("c1", RubricKind.CONCEPT, "概念", "日本はプレート境界に位置する"),
            modelAnswers = listOf("日本はプレート境界に位置する")
        )
        val f = extractor.extract("日本はプレート境界に位置すると言える。", bundle).single()
        assertTrue("score=${f.score}", f.score >= 0.75f)
        assertTrue(f.signals.any { it is Signal.ConceptDetected })
    }

    @Test
    fun `concept missing when unrelated`() = runBlocking {
        val bundle = bundleOf(
            RubricItem("c1", RubricKind.CONCEPT, "概念", "日本はプレート境界に位置する"),
            modelAnswers = listOf("日本はプレート境界に位置する")
        )
        val f = extractor.extract("地球の表面は海と陸でできている", bundle).single()
        assertTrue(f.score < 0.5f)
        assertTrue(f.signals.any { it is Signal.ConceptMissing })
    }
}
