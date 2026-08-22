package com.thuvstu.personalencyclopedia.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round 0 (M-3): 一覧スクロール（LazyColumn）のフレーム時間計測。
 * ダッシュボードの一覧をフリングし、50,000件規模データでのスクロール滑らかさを数字で持つ。
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollDashboardList() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        val centerX = device.displayWidth / 2
        val flingStartY = (device.displayHeight * 0.8).toInt()
        val flingEndY = (device.displayHeight * 0.3).toInt()
        repeat(3) {
            device.swipe(centerX, flingStartY, centerX, flingEndY, 25)
            device.waitForIdle()
        }
    }
}
