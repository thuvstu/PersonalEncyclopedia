plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    // Round 0 (M-2): Macrobenchmarkモジュール(:benchmark)用。ルートでバージョンを確定させておく
    alias(libs.plugins.android.test) apply false
}