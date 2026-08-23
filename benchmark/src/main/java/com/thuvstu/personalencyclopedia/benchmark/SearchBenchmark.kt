package com.thuvstu.personalencyclopedia.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round 0 (M-3) / Round 5 (PERF-8): 検索応答時間の計測。
 * 検索画面でクエリを入力し、FTS+Nグラム検索→結果一覧再描画までのフレーム時間を記録する。
 * 50,000件投入後に実施すること（docs/perf/BASELINE.md 参照）。
 */
@RunWith(AndroidJUnit4::class)
class SearchBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun searchQueryResponse() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            clickBottomTab("検索")
        }
    ) {
        val field = device.findObjects(By.clazz("android.widget.EditText")).firstOrNull()
            ?: error("Search field not found")
        // setText は Compose の SetText semantics action 経由で onValueChange を発火させる
        field.setText("合成")
        device.waitForIdle()
        field.setText("用語")
        device.waitForIdle()
    }
}
