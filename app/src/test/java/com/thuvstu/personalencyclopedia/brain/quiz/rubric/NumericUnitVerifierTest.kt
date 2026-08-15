package com.thuvstu.personalencyclopedia.brain.quiz.rubric

import com.thuvstu.personalencyclopedia.brain.quiz.EraConverter
import com.thuvstu.personalencyclopedia.brain.quiz.MultiStageGrader
import com.thuvstu.personalencyclopedia.db.dao.EraMasterDao
import com.thuvstu.personalencyclopedia.db.entity.EraMasterEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 数値・単位・式の決定論的検証テスト。
 * 新採点システム.txt の「72 km/h = 20 m/s」「1600年 = 慶長5年」を明示ケースとして検証する。
 */
class NumericUnitVerifierTest {

    private val eras = listOf(
        EraMasterEntity("天正", 1573, 1592, 3),
        EraMasterEntity("慶長", 1596, 1615, 5),
        EraMasterEntity("元禄", 1688, 1704, 17),
        EraMasterEntity("令和", 2019, null, 45)
    )

    private val fakeDao = object : EraMasterDao {
        override suspend fun getAll(): List<EraMasterEntity> = eras
        override suspend fun getByName(name: String): EraMasterEntity? =
            eras.firstOrNull { it.name == name }
    }

    private lateinit var verifier: NumericUnitVerifier

    @Before
    fun setup() {
        verifier = NumericUnitVerifier(MultiStageGrader(EraConverter(fakeDao)))
    }

    @Test
    fun `72 km per hour equals 20 m per s`() = runBlocking {
        val r = verifier.verify("72 km/h", "20 m/s")
        assertEquals(true, r.matched)
        assertTrue(r.detail, r.detail.contains("単位換算一致"))
    }

    @Test
    fun `12 km equals 12000 m`() = runBlocking {
        val r = verifier.verify("12 km", "12000 m")
        assertEquals(true, r.matched)
    }

    @Test
    fun `100 celsius equals 212 fahrenheit`() = runBlocking {
        val r = verifier.verify("100°C", "212°F")
        assertEquals(true, r.matched)
    }

    @Test
    fun `1600年 equals 慶長5年 via era master`() = runBlocking {
        val r = verifier.verify("慶長5年", "1600年")
        assertEquals(true, r.matched)
        assertTrue(r.detail.contains("数値一致"))
    }

    @Test
    fun `mismatched values are incorrect`() = runBlocking {
        val r = verifier.verify("5 km", "3 km")
        assertEquals(false, r.matched)
    }

    @Test
    fun `no numeric content is undeterminable`() = runBlocking {
        val r = verifier.verify("蒸気機関車が発明された", "関ヶ原の戦い")
        assertNull("数値が無ければ判定不能(null)", r.matched)
    }

    @Test
    fun `unit mismatch without equivalence is incorrect`() = runBlocking {
        val r = verifier.verify("72 km/h", "72 mph")
        assertEquals(false, r.matched)
    }

    @Test
    fun `extract numbers with units`() {
        val nums = verifier.extractNumbers("72 km/h と 12000 m")
        assertEquals(2, nums.size)
        assertEquals("km/h", nums[0].unit)
        assertEquals("m", nums[1].unit)
    }
}
