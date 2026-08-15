# Walkthrough 3 — GAP以降ラウンド実装(Round E / G1 / G2)とテスト整備

**対象:** `docs/PersonalEncyclopedia-GAP以降実行計画-v6.md` の Round E(和暦マスタ)・Round G1(LocalServer分割)・Round G2(マイグレーションテスト)、および既存テストの修正。
**作業日:** 2026-08-15(本セッション)
**前提:** Round A(検証・整理)は完了済み。Round B(SAFバックアップ)・C(APIキー暗号化/exportSchema復帰)・D(スレッドセーフ化)は作業ツリーに実装済みの状態から開始。

本セッションで**未着手だった Round E と Round G のうち G1/G2 を実装し、ブロッキングしていた既存テストを修正**した。Round F(ライブラリ更新)と Round H(実機検証)のみ未完了。

---

## 1. 全体像とラウンドの対応

| ラウンド | 内容 | 状態 |
|---|---|---|
| Round E | GAP-5: 和暦マスタ `era_master` + 採点エンジンの参照置き換え | ✅ 本セッションで完了 |
| Round G1 | GAP-6: `LocalServer.kt`(579行)をルート単位に分割 | ✅ 本セッションで完了 |
| Round G2 | `MigrationTestHelper` による v1→v7 マイグレーションテスト | ✅ 本セッションで実装(実行は実機/エミュレータ必須) |
| Round F | ライブラリバージョン更新(Kotlin/Compose/Room/Ktor/Hilt/AGP) | ⬜ 未着手(破壊的変更リスク大のため最後に個別実施) |
| Round H | 実機での既存機能検証 | ⬜ エージェントでは実行不可 |

---

## 2. Round E — GAP-5: 和暦マスタ

### 2.1 E1: `era_master` テーブル + マイグレーション v6→v7

**設計根拠:** 設計書 §5.8.4(変換式 `元年の西暦 + (yearInEra - 1)`)。シードは「江戸期以降 + 歴史教育で頻出する著名な古典元号」を優先。**entities配列・version番号・DatabaseModuleの3点セットを同一ラウンドで揃える**(Round Aで削除された孤立`Migration6to7.kt`の反省)。

#### `db/entity/EraMasterEntity.kt`(新規)

```kotlin
package com.thuvstu.personalencyclopedia.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 和暦マスタ(設計書§5.8.4)。
 * 元年の西暦 + (yearInEra - 1) で西暦へ変換する。
 * endYear == null は現在も継続中の元号(令和)を意味する。
 */
@Entity(tableName = "era_master")
data class EraMasterEntity(
    @PrimaryKey val name: String,
    val startYear: Int,
    val endYear: Int?,
    val sortOrder: Int
)
```

#### `db/dao/EraMasterDao.kt`(新規)

```kotlin
@Dao
interface EraMasterDao {
    @Query("SELECT * FROM era_master ORDER BY startYear DESC")
    suspend fun getAll(): List<EraMasterEntity>

    @Query("SELECT * FROM era_master WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): EraMasterEntity?
}
```

#### `db/Migration6to7.kt`(新規)

`CREATE TABLE IF NOT EXISTS era_master` + `INSERT OR REPLACE` で56件の元号をシード投入。
構成は 天文(1532)〜慶長(1596) の戦国〜安土桃山、寛永(1624)〜慶応(1865) の江戸全期、
明治(1868)〜令和(2019〜、endYear=null) の近現代、天平(729)〜文明(1487) の著名古典元号。

```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `era_master` (
                `name` TEXT NOT NULL PRIMARY KEY,
                `startYear` INTEGER NOT NULL,
                `endYear` INTEGER,
                `sortOrder` INTEGER NOT NULL
            )
        """)

        insertEra(db, "天文", 1532, 1555, 1)
        insertEra(db, "永禄", 1558, 1570, 2)
        insertEra(db, "天正", 1573, 1592, 3)
        insertEra(db, "文禄", 1592, 1596, 4)
        insertEra(db, "慶長", 1596, 1615, 5)
        // …(江戸・近現代・古典元号は省略)…
        insertEra(db, "明治", 1868, 1912, 41)
        insertEra(db, "大正", 1912, 1926, 42)
        insertEra(db, "昭和", 1926, 1989, 43)
        insertEra(db, "平成", 1989, 2019, 44)
        insertEra(db, "令和", 2019, null, 45)
        // …(天平〜文明 11件)…
    }

    private fun insertEra(db: SupportSQLiteDatabase, name: String, startYear: Int, endYear: Int?, sortOrder: Int) {
        db.execSQL(
            "INSERT OR REPLACE INTO `era_master` (`name`, `startYear`, `endYear`, `sortOrder`) VALUES (?, ?, ?, ?)",
            arrayOf(name, startYear, endYear, sortOrder)
        )
    }
}
```

#### `db/AppDatabase.kt` 更新

- `entities` に `EraMasterEntity::class` を追加
- `abstract fun eraMasterDao(): EraMasterDao` を追加
- `version = 7` へ引き上げ

#### `di/DatabaseModule.kt` 更新

- `addMigrations(...)` に `MIGRATION_6_7` を追加(1_2〜6_7の全チェーン)
- `provideEraMasterDao(db: AppDatabase): EraMasterDao` を追加

### 2.2 E2: `Grader.kt` のハードコード配列を `era_master` 参照へ置き換え

**変更前:** `japaneseEras = listOf("令和" to 2018, ...)` の5元号ハードコード(西暦オフセットが1年ずれていた)。

**変更後:**
1. `MultiStageGrader` を `object` → `@Singleton class @Inject constructor(eraConverter: EraConverter)` 化
2. `parseYear`/`gradeNumeric`/`grade` を `suspend` 化(DAO呼び出しのため)
3. `GradeResult` に `undeterminable: Boolean = false` を追加(設計書§8.9: 元号データ不足は「不一致」でなく「判定不能」を明示)
4. 未知元号の検出 `detectUnknownEra()` を追加

#### `brain/quiz/EraConverter.kt`(新規)

```kotlin
@Singleton
class EraConverter @Inject constructor(
    private val eraMasterDao: EraMasterDao
) {
    suspend fun toWesternYear(eraName: String, yearInEra: Int): Int? {
        val era = eraMasterDao.getByName(eraName) ?: return null
        return era.startYear + (yearInEra - 1)
    }

    suspend fun getAll(): List<EraMasterEntity> = eraMasterDao.getAll()
}
```

#### `Grader.kt` の和暦変換・判定不能検出(抜粋)

```kotlin
suspend fun parseYear(text: String): Int? {
    Regex("(\\d{3,4})年?").find(text)?.let { return it.groupValues[1].toIntOrNull() }
    for (era in eraConverter.getAll()) {
        val m = Regex("${era.name}(\\d{1,2})年?").find(text)
        if (m != null) {
            val yearInEra = m.groupValues[1].toIntOrNull() ?: continue
            return era.startYear + (yearInEra - 1)
        }
        if (text.contains("${era.name}元")) return era.startYear
    }
    Regex("紀元前(\\d+)年?").find(text)?.let {
        return -(it.groupValues[1].toIntOrNull() ?: return null)
    }
    return null
}

private suspend fun detectUnknownEra(text: String): Boolean {
    if (text.isBlank()) return false
    val known = eraConverter.getAll().map { it.name }
    val eraPattern = Regex("([\\u4E00-\\u9FFF]{2,3})(\\d{1,2})年?|([\\u4E00-\\u9FFF]{2,3})元")
    for (m in eraPattern.findAll(text)) {
        val candidate = m.groupValues[1].ifBlank { m.groupValues[3] }
        if (candidate.isNotBlank() && candidate !in known) return true
    }
    return false
}
```

`gradeNumeric` は、`parseYear` が null かつ `detectUnknownEra` が真の場合、
`GradeResult(false, 0f, "numeric", undeterminable = true)` を返し、`grade` 本管で `undeterminable` を最終結果として早期リターンする。

#### 呼び出し側の追従

- `repository/QuizRepository.kt`: コンストラクタに `private val multiStageGrader: MultiStageGrader` を追加し、`MultiStageGrader.grade(...)` → `multiStageGrader.grade(...)` に変更
- `server/QuizRoutes.kt`: `ServerDependencies` 経由で注入した `deps.multiStageGrader.grade(...)` を使用

### 2.3 E3: 採点エンジンのテスト(red→green)

#### `app/src/test/.../brain/quiz/MultiStageGraderTest.kt`(新規)

DAOをスタブ(`EraMasterDao` の匿名実装)にして JVM 単体テストで実行。設計書§8.9の明示ケース「1600年=慶長5年」を含む。

```kotlin
@Before
fun setup() {
    grader = MultiStageGrader(EraConverter(fakeDao))
}

@Test fun `1600年 equals 慶長5年`() = runBlocking {
    val result = grader.grade(userAnswer = "慶長5年", correctAnswer = "1600年")
    assertTrue(result.isCorrect)
    assertEquals("numeric", result.method)
}

@Test fun `明治元年 equals 1868年`() = runBlocking { ... }
@Test fun `令和6年 equals 2024年`() = runBlocking { ... }
@Test fun `天保13年 equals 1842年`() = runBlocking { ... }
@Test fun `元禄元年 equals 1688年`() = runBlocking { ... }

@Test fun `unknown era is marked undeterminable not incorrect`() = runBlocking {
    // 弘安は era_master シードデータに含まれない → 判定不能として明示
    val result = grader.grade(userAnswer = "弘安5年", correctAnswer = "1282年")
    assertFalse(result.isCorrect)
    assertTrue(result.undeterminable)
}
```

---

## 3. Round G1 — GAP-6: LocalServer 分割

**方針(設計書§10.1):** `LocalServer.kt` は認証 + ルーティング登録のみの薄いエントリポイントへ。エンドポイント実装は `server/routes/`、DTO は `server/dto/` に集約。DI のための依存の束 `ServerDependencies` を導入し、ルート関数には単一オブジェクトを渡す。

### 3.1 新規ファイル構成

```
server/
├── LocalServer.kt            # 薄いエントリポイント(認証 + ルート登録のみ)
├── ServerDependencies.kt     # ルートへ注入するDAO等の束(新規)
├── TokenManager.kt
├── dto/ApiDtos.kt            # DTO + Entity→Response マッパー(新規)
└── routes/
    ├── EntriesRoutes.kt
    ├── SearchRoutes.kt
    ├── SrsRoutes.kt
    ├── QuizRoutes.kt
    ├── ConnectionRoutes.kt
    ├── GraphRoutes.kt
    ├── ProgressRoutes.kt
    └── PluginRoutes.kt
```

### 3.2 `server/ServerDependencies.kt`(新規)

```kotlin
class ServerDependencies @Inject constructor(
    val entryDao: EntryDao,
    val thoughtDao: EntryThoughtDao,
    val definitionDao: EntryDefinitionDao,
    val srsReviewDao: SrsReviewDao,
    val quizDao: QuizDao,
    val connectionDao: ConnectionDao,
    val progressEventDao: ProgressEventDao,
    val pluginDao: PluginDao,
    val multiStageGrader: MultiStageGrader
)
```

### 3.3 `server/LocalServer.kt`(書き換え後)

```kotlin
@Singleton
class LocalServer @Inject constructor(
    private val tokenManager: TokenManager,
    private val deps: ServerDependencies
) {
    fun start(port: Int = 8080) {
        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) { json(...) }
            install(Authentication) { bearer("token-auth") { ... } }
            routing {
                get("/health") { ... }
                authenticate("token-auth") {
                    route("/api") {
                        entriesRoutes(deps)
                        searchRoutes(deps)
                        srsRoutes(deps)
                        quizRoutes(deps)
                        connectionRoutes(deps)
                        graphRoutes(deps)
                        progressRoutes(deps)
                        pluginRoutes(deps)
                    }
                }
            }
        }.also { it.start(wait = false); isRunning = true }
    }
    // stop() は従来どおり
}
```

### 3.4 `server/dto/ApiDtos.kt`(新規)

`@Serializable` なリクエスト/レスポンス DTO(`EntryResponse`/`SrsDueResponse`/`SrsReviewRequest`/`QuizResponse`/`QuizAttemptRequest`/`ConnectionResponse`/`GraphNodeResponse`/`HeatmapResponse`/`PluginResponse`/`ErrorResponse`)と、Entity→DTO のマッパー拡張関数(`EntryEntity.toResponse()`、`QuizBankEntity.toQuizResponse()`)を定義。

### 3.5 ルート関数の例(`QuizRoutes.kt`)

```kotlin
fun Route.quizRoutes(deps: ServerDependencies) {
    route("/quiz") {
        get { ... deps.quizDao.getRandomQuizzes(types, limit).map { it.toQuizResponse() } }
        get("/{id}") { ... }
        post("/{id}/attempt") {
            val gradeResult = deps.multiStageGrader.grade(
                userAnswer = body.userAnswer,
                correctAnswer = quiz.answer
            )
            // …スコア決定・attempt記録・応答…
        }
        get("/count") { ... }
    }
}
```

### 3.6 `di/ServerModule.kt` 整理

旧 `provideLocalServer(...)`(全DAOを列挙した巨大プロバイダ)を削除し、`TokenManager` のプロバイダのみに簡素化。`LocalServer` と `ServerDependencies` は `@Inject constructor` で解決されるためプロバイダ不要。

---

## 4. Round G2 — MigrationTestHelper によるマイグレーションテスト

### 4.1 依存の追加

#### `gradle/libs.versions.toml`

```toml
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-test-runner = { group = "androidx.test", name = "runner", version = "1.6.2" }
androidx-test-core = { group = "androidx.test", name = "core", version = "1.6.1" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version = "1.2.1" }
```

#### `app/build.gradle.kts`

```kotlin
defaultConfig {
    ...
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}
dependencies {
    ...
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
```

### 4.2 スキーマJSONの整備

Round C2 で復帰した `app/schemas/.../AppDatabase/` を確認し、**3.json/4.json/5.json が存在しない**ことを確認した。`MigrationTestHelper` は開始スキーマと最終スキーマの2つだけが必要(検証は最終バージョンのスキーマとの**構造比較**であり、開始スキーマの identityHash は実質未検証)なので、**v1→v7 のフルチェーンテストは 1.json + 7.json で可能**と判断。

- `1.json`: 2.json から Phase-1 以降のテーブル(topic / entry_topic / srs_review / quiz_bank / quiz_attempts)と views を除き手書き再構成(Phase-0テーブル構造は2.json/7.jsonと同一)
- `7.json`: Room による自動生成(era_master を含むことを確認済み)

### 4.3 `app/src/androidTest/.../db/MigrationTest.kt`(新規)

3本のテストを実装:

1. **`migrate1To7_preservesDataAndAddsEraMaster`** — 1.json で作成した DB に entry/entry_type/entry_thought/tag/entry_tag を投入 → `MIGRATION_1_2`〜`MIGRATION_6_7` を適用し `runMigrationsAndValidate` で v7 スキーマと構造一致を検証 → データ保持(entry・entry_tag)と `era_master` シード(慶長=1596、令和endYear=null、計56件)を検証
2. **`migrate2To7_works`** — 2.json(実在の追跡済みスキーマ)から v2 データ投入 → v2→v7 適用で保持検証
3. **`migrate6To7_addsEraMaster`** — 6.json から v6 データ投入 → `MIGRATION_6_7` のみ適用で era_master 追加を検証

```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList()
    )

    private val allMigrations = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
    )

    @Test
    fun migrate1To7_preservesDataAndAddsEraMaster() {
        helper.createDatabase(testDb, 1).use { db ->
            db.execSQL("INSERT INTO entry_type (...) VALUES (...)")
            db.execSQL("INSERT INTO entry (id, type, title, ...) VALUES ('e1', ...)")
            // entry_thought / tag / entry_tag も投入
        }

        helper.runMigrationsAndValidate(testDb, 1, true, *allMigrations).use { db ->
            val entryCount = db.query("SELECT COUNT(*) FROM entry WHERE id = 'e1'").use { c ->
                c.moveToFirst(); c.getInt(0)
            }
            assertEquals(1, entryCount)   // Phase-0 データ保持

            val keicho = db.query("SELECT startYear FROM era_master WHERE name = '慶長'").use { c ->
                c.moveToFirst(); c.getInt(0)
            }
            assertEquals(1596, keicho)    // era_master シード投入
        }
    }
    // migrate2To7_works / migrate6To7_addsEraMaster は同様
}
```

### 4.4 実行方法と現状

- コンパイル: `:app:compileDebugAndroidTestKotlin` 成功
- 実行: `:app:connectedDebugAndroidTest`(要・実機/エミュレータ)
- 本環境ではエミュレータ起動が **`FATAL: Encryption is requested but failed to create encrypt partition.`** で失敗し、`adb devices` も空のため実行不可。AVD `Medium_Phone`(android-36 playstore イメージ)が暗号化パーティションを作れない環境要因。Round A で実機利用実績があるため、実機で `connectedDebugAndroidTest` を実行するのが現実的な次の一手。

---

## 5. 既存テストの修正(ブロッカー解消)

### `app/src/test/.../brain/search/InMemoryVectorIndexConcurrencyTest.kt`

**問題:** テストメソッドが `fun name() = runBlocking { ... }` の式ボディだったため、戻り値が `List<Unit>` になり **JUnit4 の `initializationError`(戻り値 void 必須)で全テストが起動できず**、`testDebugUnitTest` が失敗していた。

**修正:** ブロックボディ `fun name() { runBlocking { ... } }` へ変更。併せて末尾アサーションをブロック内へ移動。

```kotlin
@Test
fun `concurrent topK and addVector do not crash or corrupt`() {
    runBlocking {
        val jobs = (0 until 50).map { t ->
            async(Dispatchers.Default) {
                if (t % 2 == 0) index.topK(queryVec, 5)
                else index.addVector("concurrent-entry-$t", FloatArray(4) { t.toFloat() })
            }
        }
        jobs.awaitAll()
    }
}
```

---

## 6. コンパイルエラーとその対応(Kotlin 2.1.0 の制約)

### 6.1 Grader.kt — inline ラムダ内の `continue`

Kotlin 2.1.0 では **inline ラムダ(`let`)内の `break`/`continue` は未サポート**(2.2 で追加)。当初 `Regex(...).find(text)?.let { m -> ... ?: continue }` と書いたがエラーになったため、`let` をやめて for 直下に展開した。

```kotlin
for (era in eraConverter.getAll()) {
    val m = Regex("${era.name}(\\d{1,2})年?").find(text)
    if (m != null) {
        val yearInEra = m.groupValues[1].toIntOrNull() ?: continue
        return era.startYear + (yearInEra - 1)
    }
    if (text.contains("${era.name}元")) return era.startYear
}
```

### 6.2 MigrationTest.kt — MigrationTestHelper のコンストラクタ

Room 2.7.1 の `MigrationTestHelper` には **`(Instrumentation, Class, List, boolean allowMainThreadQueries)` 形式が存在しない**(2.6系で削除済み)。`javap` で実体のシグネチャを確認し、`(Instrumentation, Class<out RoomDatabase>, List<AutoMigrationSpec>)` に修正した。

```kotlin
MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    AppDatabase::class.java,
    emptyList()
)
```

### 6.3 ServerModule.kt — 旧プロバイダの残留参照

G1 で `ServerDependencies` 導入後も `provideLocalServer(...)` が旧コンストラクタ参照を残しておりコンパイルエラー。削除して解決。

---

## 7. 検証結果

| 項目 | コマンド | 結果 |
|---|---|---|
| Unit テスト(既存+E3) | `:app:testDebugUnitTest` | ✅ グリーン |
| androidTest コンパイル | `:app:compileDebugAndroidTestKotlin` | ✅ 成功 |
| スキーマ生成 | Room 自動生成 | ✅ 7.json に era_master を含む |
| マイグレーションテスト実行 | `:app:connectedDebugAndroidTest` | ⬜ エミュレータが暗号化エラーで起動不能(環境要因) |

---

## 8. 残タスクと次の一手

1. **Round F(ライブラリ更新)** — Kotlin 2.1.0→2.4.0(KSP2移行)、Compose BOM 2025.01.01→2026最新、Room 2.7.1→2.8.4、Ktor 3.1.1→3.5.1、Hilt/AGP。計画どおり「1更新ごとに個別ビルド」。Room 2.8.x への更新は `room-testing` のコンストラクタ・`MigrationTestHelper` の振る舞いも再確認が必要。**Round F 前後で MigrationTest / MultiStageGraderTest がグリーンなままであることを確認する**
2. **G2 の実行** — 実機接続後に `:app:connectedDebugAndroidTest` を実行し `MigrationTest` 3本をグリーンにする(エミュレータは `Encryption is requested...` で不可)
3. **Round H(実機検証)** — H1〜H5 はエージェントでは実行不可。ユーザーが実機で確認
4. **コミット** — 本セッションの変更は未コミット(計画v6の各Round🛑ごとにコミット推奨)。master は `7e228ff`、作業ツリーに Round B〜G の変更が積まれている
