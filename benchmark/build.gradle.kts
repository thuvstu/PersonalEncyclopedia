plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

// Round 0 (M-2): Macrobenchmarkモジュール。
// cold start・画面遷移・検索応答を実機/エミュレータで計測する。
// 実行: ./gradlew :benchmark:pixel8Api34BenchmarkAndroidTest または Studioから実行
android {
    namespace = "com.thuvstu.personalencyclopedia.benchmark"
    compileSdk = 37

    // 計測対象モジュール（benchmarkビルドタイプは release にフォールバックする）
    targetProjectPath = ":app"

    defaultConfig {
        minSdk = 28
        targetSdk = 35

        testInstrumentationRunner = "androidx.benchmark.macro.junit4.MacrobenchmarkRunner"
    }

    buildTypes {
        // benchmark ビルドタイプは対象アプリ(:app)の release にフォールバックする
        create("benchmark") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions {
        managedDevices {
            // AGP 9: devices{} → localDevices{} に変更されている
            localDevices {
                create("pixel8Api34") {
                    device = "Pixel 8"
                    apiLevel = 34
                    systemImageSource = "aosp-atd" // 計測オーバーヘッドの少ないATDイメージ
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
}
