package com.thuvstu.personalencyclopedia.brain.quiz

import com.thuvstu.personalencyclopedia.db.dao.EraMasterDao
import com.thuvstu.personalencyclopedia.db.entity.EraMasterEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E3 (GAP-5): 多段階採点エンジンの和暦変換テスト。
 * 設計書§8.9の「1600年=慶長5年」を明示ケースとして検証する。
 */
class MultiStageGraderTest {

    private val eras = listOf(
        EraMasterEntity("天正", 1573, 1592, 3),
        EraMasterEntity("慶長", 1596, 1615, 5),
        EraMasterEntity("元禄", 1688, 1704, 17),
        EraMasterEntity("享保", 1716, 1736, 20),
        EraMasterEntity("寛政", 1789, 1801, 29),
        EraMasterEntity("天保", 1830, 1844, 33),
        EraMasterEntity("明治", 1868, 1912, 41),
        EraMasterEntity("大正", 1912, 1926, 42),
        EraMasterEntity("昭和", 1926, 1989, 43),
        EraMasterEntity("平成", 1989, 2019, 44),
        EraMasterEntity("令和", 2019, null, 45)
    )

    private val fakeDao = object : EraMasterDao {
        override suspend fun getAll(): List<EraMasterEntity> = eras
        override suspend fun getByName(name: String): EraMasterEntity? =
            eras.firstOrNull { it.name == name }
    }

    private lateinit var grader: MultiStageGrader

    @Before
    fun setup() {
        grader = MultiStageGrader(EraConverter(fakeDao))
    }

    @Test
    fun `1600年 equals 慶長5年`() = runBlocking {
        val result = grader.grade(userAnswer = "慶長5年", correctAnswer = "1600年")
        assertTrue("慶長5年 should match 1600年", result.isCorrect)
        assertEquals("numeric", result.method)
    }

    @Test
    fun `慶長5年 equals 1600年 (inverse direction)`() = runBlocking {
        val result = grader.grade(userAnswer = "1600年", correctAnswer = "慶長5年")
        assertTrue("1600年 should match 慶長5年", result.isCorrect)
        assertEquals("numeric", result.method)
    }

    @Test
    fun `明治元年 equals 1868年`() = runBlocking {
        val result = grader.grade(userAnswer = "明治元年", correctAnswer = "1868年")
        assertTrue("明治元年 should match 1868年", result.isCorrect)
    }

    @Test
    fun `令和6年 equals 2024年`() = runBlocking {
        val result = grader.grade(userAnswer = "令和6年", correctAnswer = "2024年")
        assertTrue("令和6年 should match 2024年", result.isCorrect)
    }

    @Test
    fun `天保13年 equals 1842年`() = runBlocking {
        val result = grader.grade(userAnswer = "天保13年", correctAnswer = "1842年")
        assertTrue("天保13年 should match 1842年", result.isCorrect)
    }

    @Test
    fun `元禄元年 equals 1688年`() = runBlocking {
        val result = grader.grade(userAnswer = "元禄元年", correctAnswer = "1688年")
        assertTrue("元禄元年 should match 1688年", result.isCorrect)
    }

    @Test
    fun `parseYear converts era to western year`() = runBlocking {
        assertEquals(1596, grader.parseYear("慶長元年"))
        assertEquals(1600, grader.parseYear("慶長5年"))
        assertEquals(2024, grader.parseYear("令和6年"))
    }

    @Test
    fun `unknown era is marked undeterminable not incorrect`() = runBlocking {
        // 弘安は era_master シードデータに含まれない → 判定不能として明示
        val result = grader.grade(userAnswer = "弘安5年", correctAnswer = "1282年")
        assertFalse(result.isCorrect)
        assertTrue("未知の元号は undeterminable になるべき", result.undeterminable)
    }
}
