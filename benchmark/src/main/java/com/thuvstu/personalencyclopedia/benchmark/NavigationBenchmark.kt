package com.thuvstu.personalencyclopedia.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round 0 (M-3): 主要画面遷移（ボトムナビ）のフレーム時間計測。
 * ホーム → 検索 → 統計 → ホーム の遷移で jank (16ms超のフレーム) を検出する。
 */
@RunWith(AndroidJUnit4::class)
class NavigationBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun navigateBottomTabs() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        clickBottomTab("検索")
        clickBottomTab("統計")
        clickBottomTab("ホーム")
    }
}
