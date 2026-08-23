package com.thuvstu.personalencyclopedia.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2

/** 計測対象アプリの共通定数 */
const val TARGET_PACKAGE = "com.thuvstu.personalencyclopedia"

/**
 * ボトムナビのタブをラベルでクリックする。
 * NavigationBarItemはclickableで子semanticsがmergeされるため、
 * 同名テキスト(画面タイトル等)との重複は isClickable で絞り込む。
 */
fun MacrobenchmarkScope.clickBottomTab(label: String) {
    val candidates = device.findObjects(By.text(label))
    val target: UiObject2 = candidates.firstOrNull { it.isClickable }
        ?: candidates.lastOrNull()
        ?: error("Bottom tab not found: $label")
    target.click()
    device.waitForIdle()
}
