はじめに添付markdownファイルをよく読んでください。その計画に基づき実装を行う。
現在Phase0の実装を終えている。
Phase 0 完了時点のまとめ
実装済み要素	対応セクション
Room DB: entry + entry_type + entry_thought + entry_definition + tag + entry_tag	§5.2〜5.4
DAO: CRUD + LIKE検索 + お気に入り + 論理削除 + accessedAt更新	§5.3
Hilt DI: Database/DAO/Repository/Server	§9.2
リポジトリ: ThoughtDraft/DefinitionDraft による作成・更新	—
ViewModel: Dashboard / Search / ThoughtEdit / DefinitionEdit / EntryDetail / Server	—
Ktorサーバー: Bearer認証 + /api/entries + /api/search	§10
UI: ダッシュボード(クイック追加・最近の追加・統計)	§11.3
UI: 検索(LIKE + タイプフィルタ)	§11.3
UI: メモ(thought)編集 / 単語帳(definition)編集	§11.3
UI: エントリー詳細(タグ管理・お気に入り・削除)	§11.3
UI: 設定(サーバーON/OFF・トークン管理)	§4.3
エントリー型カラーシステム	§11.2
エンプティステート	§11.1
シードデータ(13型のentry_type)	§5.2
BottomNavigation(ホーム・検索)	§11.3

以下がPhase0コード
# Phase 0 実装 — 毎日使える最小版

以下、設計書のフェーズ0に基づき、ビルド可能な完全なコードを一気に出力します。

---

## 1. ビルド設定

### `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google { content { includeGroupByRegex("com\\.android.*") ; includeGroupByRegex("com\\.google.*") ; includeGroupByRegex("androidx.*") } }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "PersonalEncyclopedia"
include(":app")
```
### `gradle/libs.versions.toml`
```properties
# Gradle / JVM
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8 -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=false

# Kotlin
kotlin.code.style=official
kotlin.incremental=true

# AndroidX（必須）
android.useAndroidX=true
android.nonTransitiveRClass=true
android.nonFinalResIds=true

# Compose
android.defaults.buildfeatures.buildconfig=false
```

### `gradle/libs.versions.toml`
```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
compose-bom = "2025.01.01"
room = "2.7.1"
hilt = "2.54"
hilt-navigation-compose = "1.2.0"
ktor = "3.1.1"
lifecycle = "2.8.7"
navigation-compose = "2.8.6"
coroutines = "1.10.1"
serialization = "1.7.3"
datastore = "1.1.3"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version = "1.10.0" }

# Lifecycle
lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# Navigation
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }

# Ktor Server
ktor-server-core = { group = "io.ktor", name = "ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { group = "io.ktor", name = "ktor-server-netty", version.ref = "ktor" }
ktor-server-content-negotiation = { group = "io.ktor", name = "ktor-server-content-negotiation", version.ref = "ktor" }
ktor-server-auth = { group = "io.ktor", name = "ktor-server-auth", version.ref = "ktor" }
ktor-serialization-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }

# Kotlin
kotlinx-coroutines = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }

# DataStore
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# Core
core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.15.0" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

### `build.gradle.kts` (project)
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

### `app/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.thuvstu.personalencyclopedia"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thuvstu.personalencyclopedia"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/compat.schemas"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // Navigation
    implementation(libs.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.serialization.json)

    // Kotlin
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)

    // DataStore
    implementation(libs.datastore.preferences)

    // Core
    implementation(libs.core.ktx)
}```

---

## 2. データベース層

### `app/src/main/java/com/example/encyclopedia/db/entity/EntryTypeEntity.kt`
```kotlin
package com.example.encyclopedia.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entry_type")
data class EntryTypeEntity(
    @PrimaryKey val name: String,
    val labelJa: String,
    val icon: String? = null,
    val colorHex: String,
    val isActive: Boolean = true,
    val sortOrder: Int = 0
)
```

### `app/src/main/java/com/example/encyclopedia/db/entity/EntryEntity.kt`
```kotlin
package com.example.encyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "entry",
    indices = [
        Index("type"),
        Index("createdAt"),
        Index("accessedAt"),
        Index("isFavorite"),
        Index("isMuted"),
        Index("deletedAt")
    ]
)
data class EntryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: String,
    val title: String,
    val content: String? = null,
    val summary: String? = null,
    val sourceUrl: String? = null,
    val lang: String? = null,
    val isFavorite: Boolean = false,
    val isMuted: Boolean = false,
    val accessedAt: Long? = null,
    val deletedAt: Long? = null,
    val metadataJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

### `app/src/main/java/com/example/encyclopedia/db/entity/EntryThoughtEntity.kt`
```kotlin
package com.example.encyclopedia.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "entry_thought",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EntryThoughtEntity(
    @PrimaryKey val entryId: String,
    val mood: String? = null,
    val context: String? = null,
    val isDraft: Boolean = false
)
```

### `app/src/main/java/com/example/encyclopedia/db/entity/EntryDefinitionEntity.kt`
```kotlin
package com.example.encyclopedia.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "entry_definition",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EntryDefinitionEntity(
    @PrimaryKey val entryId: String,
    val term: String,
    val reading: String? = null,
    val definition: String,
    val field: String? = null,
    val examplesJson: String = "[]",
    val relatedTermsJson: String = "[]"
)
```

### `app/src/main/java/com/example/encyclopedia/db/entity/TagEntity.kt`
```kotlin
package com.example.encyclopedia.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "tag",
    indices = [Index("name", unique = true)]
)
data class TagEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String? = null
)
```

### `app/src/main/java/com/example/encyclopedia/db/entity/EntryTagEntity.kt`
```kotlin
package com.example.encyclopedia.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "entry_tag",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(EntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tagId")]
)
data class EntryTagEntity(
    val entryId: String,
    val tagId: String
)
```

### `app/src/main/java/com/example/encyclopedia/db/dao/EntryTypeDao.kt`
```kotlin
package com.example.encyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.encyclopedia.db.entity.EntryTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryTypeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(types: List<EntryTypeEntity>)

    @Query("SELECT * FROM entry_type WHERE isActive = 1 ORDER BY sortOrder")
    fun observeAll(): Flow<List<EntryTypeEntity>>

    @Query("SELECT * FROM entry_type WHERE name = :name")
    suspend fun getByName(name: String): EntryTypeEntity?
}
```

### `app/src/main/java/com/example/encyclopedia/db/dao/EntryDao.kt`
```kotlin
package com.example.encyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.encyclopedia.db.entity.EntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Insert
    suspend fun insert(entry: EntryEntity)

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("UPDATE entry SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE entry SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE entry SET isFavorite = :fav, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE entry SET accessedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun getById(id: String): EntryEntity?

    @Query("SELECT * FROM entry WHERE id = :id")
    fun observeById(id: String): Flow<EntryEntity?>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun observeAll(limit: Int = 50, offset: Int = 0): Flow<List<EntryEntity>>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL AND type = :type
        ORDER BY createdAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun observeByType(type: String, limit: Int = 50, offset: Int = 0): Flow<List<EntryEntity>>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL
          AND (title LIKE '%' || :q || '%' OR content LIKE '%' || :q || '%')
        ORDER BY
          CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END,
          createdAt DESC
        LIMIT :limit
    """)
    fun search(q: String, limit: Int = 50): Flow<List<EntryEntity>>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL AND isFavorite = 1
        ORDER BY createdAt DESC
    """)
    fun observeFavorites(): Flow<List<EntryEntity>>

    @Query("SELECT COUNT(*) FROM entry WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM entry WHERE deletedAt IS NULL AND type = :type")
    fun observeCountByType(type: String): Flow<Int>

    @Query("""
        SELECT * FROM entry
        WHERE deletedAt IS NULL
        ORDER BY createdAt DESC
        LIMIT :limit
    """)
    fun observeRecent(limit: Int = 10): Flow<List<EntryEntity>>
}
```

### `app/src/main/java/com/example/encyclopedia/db/dao/EntryThoughtDao.kt`
```kotlin
package com.example.encyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.encyclopedia.db.entity.EntryThoughtEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryThoughtDao {
    @Insert
    suspend fun insert(thought: EntryThoughtEntity)

    @Update
    suspend fun update(thought: EntryThoughtEntity)

    @Query("SELECT * FROM entry_thought WHERE entryId = :entryId")
    suspend fun getByEntryId(entryId: String): EntryThoughtEntity?

    @Query("SELECT * FROM entry_thought WHERE entryId = :entryId")
    fun observeByEntryId(entryId: String): Flow<EntryThoughtEntity?>
}
```

### `app/src/main/java/com/example/encyclopedia/db/dao/EntryDefinitionDao.kt`
```kotlin
package com.example.encyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.encyclopedia.db.entity.EntryDefinitionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDefinitionDao {
    @Insert
    suspend fun insert(def: EntryDefinitionEntity)

    @Update
    suspend fun update(def: EntryDefinitionEntity)

    @Query("SELECT * FROM entry_definition WHERE entryId = :entryId")
    suspend fun getByEntryId(entryId: String): EntryDefinitionEntity?

    @Query("SELECT * FROM entry_definition WHERE entryId = :entryId")
    fun observeByEntryId(entryId: String): Flow<EntryDefinitionEntity?>

    @Query("""
        SELECT ed.* FROM entry_definition ed
        INNER JOIN entry e ON e.id = ed.entryId
        WHERE e.deletedAt IS NULL
          AND (ed.term LIKE '%' || :q || '%' OR ed.definition LIKE '%' || :q || '%')
        ORDER BY e.createdAt DESC
        LIMIT :limit
    """)
    fun search(q: String, limit: Int = 50): Flow<List<EntryDefinitionEntity>>
}
```

### `app/src/main/java/com/example/encyclopedia/db/dao/TagDao.kt`
```kotlin
package com.example.encyclopedia.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.encyclopedia.db.entity.EntryTagEntity
import com.example.encyclopedia.db.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tag ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Query("""
        SELECT t.* FROM tag t
        INNER JOIN entry_tag et ON et.tagId = t.id
        WHERE et.entryId = :entryId
        ORDER BY t.name
    """)
    fun observeTagsForEntry(entryId: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkTag(link: EntryTagEntity)

    @Query("DELETE FROM entry_tag WHERE entryId = :entryId AND tagId = :tagId")
    suspend fun unlinkTag(entryId: String, tagId: String)
}
```

### `app/src/main/java/com/example/encyclopedia/db/AppDatabase.kt`
```kotlin
package com.example.encyclopedia.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.encyclopedia.db.dao.*
import com.example.encyclopedia.db.entity.*

@Database(
    entities = [
        EntryTypeEntity::class,
        EntryEntity::class,
        EntryThoughtEntity::class,
        EntryDefinitionEntity::class,
        TagEntity::class,
        EntryTagEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryTypeDao(): EntryTypeDao
    abstract fun entryDao(): EntryDao
    abstract fun entryThoughtDao(): EntryThoughtDao
    abstract fun entryDefinitionDao(): EntryDefinitionDao
    abstract fun tagDao(): TagDao
}
```

### `app/src/main/java/com/example/encyclopedia/db/SeedData.kt`
```kotlin
package com.example.encyclopedia.db

import com.example.encyclopedia.db.entity.EntryTypeEntity

object SeedData {
    val entryTypes = listOf(
        EntryTypeEntity("thought",    "メモ・思考",     "💭", "#8B5CF6", sortOrder = 0),
        EntryTypeEntity("definition", "用語・単語帳",   "📖", "#10B981", sortOrder = 1),
        EntryTypeEntity("webpage",    "Webページ",     "🌐", "#3B82F6", sortOrder = 2),
        EntryTypeEntity("book",       "本",            "📚", "#F59E0B", sortOrder = 3),
        EntryTypeEntity("video",      "動画",          "🎬", "#EF4444", sortOrder = 4),
        EntryTypeEntity("document",   "ドキュメント",   "📄", "#64748B", sortOrder = 5),
        EntryTypeEntity("media",      "メディア",       "🖼️", "#06B6D4", sortOrder = 6),
        EntryTypeEntity("person",     "人物",          "👤", "#EC4899", sortOrder = 7),
        EntryTypeEntity("org",        "組織",          "🏢", "#A855F7", sortOrder = 8),
        EntryTypeEntity("place",      "場所",          "📍", "#14B8A6", sortOrder = 9),
        EntryTypeEntity("event",      "出来事",        "📅", "#F97316", sortOrder = 10),
        EntryTypeEntity("liked",      "いいね済み",     "❤️", "#F43F5E", sortOrder = 11),
        EntryTypeEntity("ai_conv",    "AI会話",        "🤖", "#6366F1", sortOrder = 12),
    )
}
```

---

## 3. リポジトリ層

### `app/src/main/java/com/example/encyclopedia/repository/EntryRepository.kt`
```kotlin
package com.example.encyclopedia.repository

import com.thuvstu.personalencyclopedia.db.dao.*
import com.thuvstu.personalencyclopedia.db.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton


data class ThoughtDraft(
    val title: String,
    val content: String?,
    val mood: String? = null,
    val context: String? = null
)

data class DefinitionDraft(
    val term: String,
    val reading: String? = null,
    val definition: String,
    val field: String? = null,
    val examples: List<String> = emptyList()
)

@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val thoughtDao: EntryThoughtDao,
    private val definitionDao: EntryDefinitionDao,
    private val tagDao: TagDao,
    private val entryTypeDao: EntryTypeDao
) {
    // ── Observe ──
    fun observeRecent(limit: Int = 10): Flow<List<EntryEntity>> =
        entryDao.observeRecent(limit)

    fun observeAll(limit: Int = 50, offset: Int = 0): Flow<List<EntryEntity>> =
        entryDao.observeAll(limit, offset)

    fun observeByType(type: String, limit: Int = 50): Flow<List<EntryEntity>> =
        entryDao.observeByType(type, limit)

    fun observeFavorites(): Flow<List<EntryEntity>> =
        entryDao.observeFavorites()

    fun observeEntry(id: String): Flow<EntryEntity?> =
        entryDao.observeById(id)

    fun observeThought(entryId: String): Flow<EntryThoughtEntity?> =
        thoughtDao.observeByEntryId(entryId)

    fun observeDefinition(entryId: String): Flow<EntryDefinitionEntity?> =
        definitionDao.observeByEntryId(entryId)

    fun observeTagsForEntry(entryId: String): Flow<List<TagEntity>> =
        tagDao.observeTagsForEntry(entryId)

    fun observeAllTags(): Flow<List<TagEntity>> =
        tagDao.observeAll()

    fun observeCount(): Flow<Int> = entryDao.observeCount()

    fun search(query: String, limit: Int = 50): Flow<List<EntryEntity>> =
        entryDao.search(query, limit)

    // ── Create ──
    suspend fun createThought(draft: ThoughtDraft): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(
            EntryEntity(
                id = id,
                type = "thought",
                title = draft.title,
                content = draft.content,
                createdAt = now,
                updatedAt = now,
                accessedAt = now
            )
        )
        thoughtDao.insert(
            EntryThoughtEntity(
                entryId = id,
                mood = draft.mood,
                context = draft.context
            )
        )
        return id
    }

    suspend fun createDefinition(draft: DefinitionDraft): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        entryDao.insert(
            EntryEntity(
                id = id,
                type = "definition",
                title = draft.term,
                content = null,
                createdAt = now,
                updatedAt = now,
                accessedAt = now
            )
        )
        definitionDao.insert(
            EntryDefinitionEntity(
                entryId = id,
                term = draft.term,
                reading = draft.reading,
                definition = draft.definition,
                field = draft.field,
                examplesJson = Json.encodeToString(draft.examples)
            )
        )
        return id
    }

    // ── Update ──
    suspend fun updateThought(entryId: String, draft: ThoughtDraft) {
        val now = System.currentTimeMillis()
        entryDao.getById(entryId)?.let { existing ->
            entryDao.update(
                existing.copy(
                    title = draft.title,
                    content = draft.content,
                    updatedAt = now
                )
            )
        }
        thoughtDao.getByEntryId(entryId)?.let { existing ->
            thoughtDao.update(
                existing.copy(mood = draft.mood, context = draft.context)
            )
        }
    }

    suspend fun updateDefinition(entryId: String, draft: DefinitionDraft) {
        val now = System.currentTimeMillis()
        entryDao.getById(entryId)?.let { existing ->
            entryDao.update(
                existing.copy(
                    title = draft.term,
                    updatedAt = now
                )
            )
        }
        definitionDao.getByEntryId(entryId)?.let { existing ->
            definitionDao.update(
                existing.copy(
                    term = draft.term,
                    reading = draft.reading,
                    definition = draft.definition,
                    field = draft.field,
                    examplesJson = Json.encodeToString(draft.examples)
                )
            )
        }
    }

    // ── Delete / Favorite ──
    suspend fun softDelete(id: String) = entryDao.softDelete(id)
    suspend fun restore(id: String) = entryDao.restore(id)
    suspend fun toggleFavorite(id: String) {
        entryDao.getById(id)?.let {
            entryDao.setFavorite(id, !it.isFavorite)
        }
    }

    suspend fun touch(id: String) = entryDao.touch(id)

    // ── Tags ──
    suspend fun addTag(entryId: String, tagName: String) {
        val existing = tagDao.getByName(tagName)
        val tagId = existing?.id ?: run {
            val newId = UUID.randomUUID().toString()
            tagDao.insert(TagEntity(id = newId, name = tagName))
            newId
        }
        tagDao.linkTag(EntryTagEntity(entryId, tagId))
    }

    suspend fun removeTag(entryId: String, tagId: String) =
        tagDao.unlinkTag(entryId, tagId)
}```

---

## 4. DI（Hilt）

### `app/src/main/java/com/example/encyclopedia/di/DatabaseModule.kt`
```kotlin
package com.example.encyclopedia.di

import android.content.Context
import androidx.room.Room
import com.example.encyclopedia.db.AppDatabase
import com.example.encyclopedia.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "encyclopedia.db")
            .fallbackToDestructiveMigration()   // Phase0では許容。Phase1以降でマイグレーション導入
            .build()

    @Provides fun provideEntryTypeDao(db: AppDatabase): EntryTypeDao = db.entryTypeDao()
    @Provides fun provideEntryDao(db: AppDatabase): EntryDao = db.entryDao()
    @Provides fun provideThoughtDao(db: AppDatabase): EntryThoughtDao = db.entryThoughtDao()
    @Provides fun provideDefinitionDao(db: AppDatabase): EntryDefinitionDao = db.entryDefinitionDao()
    @Provides fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
}
```

### `app/src/main/java/com/example/encyclopedia/di/ServerModule.kt`
```kotlin
package com.example.encyclopedia.di

import android.content.Context
import com.example.encyclopedia.server.LocalServer
import com.example.encyclopedia.server.TokenManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServerModule {

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext ctx: Context): TokenManager =
        TokenManager(ctx)

    @Provides
    @Singleton
    fun provideLocalServer(tokenManager: TokenManager): LocalServer =
        LocalServer(tokenManager)
}
```

---

## 5. Ktorローカルサーバー

### `app/src/main/java/com/example/encyclopedia/server/TokenManager.kt`
```kotlin
package com.example.encyclopedia.server

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "server_prefs")

class TokenManager(private val context: Context) {

    private val tokenKey = stringPreferencesKey("access_token")

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[tokenKey] }

    suspend fun getOrCreateToken(): String {
        val existing = context.dataStore.data.first()[tokenKey]
        if (existing != null) return existing
        val newToken = UUID.randomUUID().toString()
        context.dataStore.edit { it[tokenKey] = newToken }
        return newToken
    }

    suspend fun regenerateToken(): String {
        val newToken = UUID.randomUUID().toString()
        context.dataStore.edit { it[tokenKey] = newToken }
        return newToken
    }
}
```

### `app/src/main/java/com/example/encyclopedia/server/LocalServer.kt`
```kotlin
package com.example.encyclopedia.server

import com.example.encyclopedia.db.dao.EntryDao
import com.example.encyclopedia.db.dao.EntryDefinitionDao
import com.example.encyclopedia.db.dao.EntryThoughtDao
import com.example.encyclopedia.db.entity.EntryEntity
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LocalServer(private val tokenManager: TokenManager) {

    private var server: EmbeddedServer<*, *>? = null
    var isRunning = false
        private set

    // DAOs will be set after DI initialization
    lateinit var entryDao: EntryDao
    lateinit var thoughtDao: EntryThoughtDao
    lateinit var definitionDao: EntryDefinitionDao

    fun start(port: Int = 8080) {
        if (isRunning) return
        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
            install(Authentication) {
                bearer("token-auth") {
                    authenticate { credential ->
                        val expected = tokenManager.getOrCreateToken()
                        if (credential.token == expected) {
                            UserIdPrincipal("owner")
                        } else null
                    }
                }
            }
            routing {
                get("/health") {
                    call.respond(mapOf("status" to "ok", "version" to "0.1.0"))
                }
                authenticate("token-auth") {
                    route("/api") {
                        entriesRoutes()
                        searchRoutes()
                    }
                }
            }
        }.also {
            it.start(wait = false)
            isRunning = true
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        isRunning = false
    }

    // ── Entry Routes ──
    private fun Route.entriesRoutes() {
        route("/entries") {
            get {
                val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
                val entries = entryDao.observeAll(limit, offset).first()
                call.respond(entries.map { it.toResponse() })
            }
            get("/{id}") {
                val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                val entry = entryDao.getById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("Entry not found"))
                entryDao.touch(id)
                call.respond(entry.toResponse())
            }
            delete("/{id}") {
                val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
                entryDao.softDelete(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }

    private fun Route.searchRoutes() {
        get("/search") {
            val q = call.parameters["q"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ErrorResponse("query parameter 'q' is required")
            )
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
            val results = entryDao.search(q, limit).first()
            call.respond(results.map { it.toResponse() })
        }
    }
}

@Serializable
data class ErrorResponse(val message: String, val code: String = "ERROR")

@Serializable
data class EntryResponse(
    val id: String,
    val type: String,
    val title: String,
    val content: String?,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

fun EntryEntity.toResponse() = EntryResponse(
    id = id, type = type, title = title, content = content,
    isFavorite = isFavorite, createdAt = createdAt, updatedAt = updatedAt
)
```

---

## 6. UI テーマ

### `app/src/main/java/com/example/encyclopedia/ui/theme/Color.kt`
```kotlin
package com.example.encyclopedia.ui.theme

import androidx.compose.ui.graphics.Color

// Entry Type Colors (§11.2)
val TypeWebpage    = Color(0xFF3B82F6)
val TypeThought    = Color(0xFF8B5CF6)
val TypeBook       = Color(0xFFF59E0B)
val TypeVideo      = Color(0xFFEF4444)
val TypeDocument   = Color(0xFF64748B)
val TypeDefinition = Color(0xFF10B981)
val TypePerson     = Color(0xFFEC4899)
val TypePlace      = Color(0xFF14B8A6)
val TypeEvent      = Color(0xFFF97316)
val TypeAiConv     = Color(0xFF6366F1)
val TypeLiked      = Color(0xFFF43F5E)

fun entryTypeColor(type: String): Color = when (type) {
    "webpage"    -> TypeWebpage
    "thought"    -> TypeThought
    "book"       -> TypeBook
    "video"      -> TypeVideo
    "document"   -> TypeDocument
    "definition" -> TypeDefinition
    "person"     -> TypePerson
    "place"      -> TypePlace
    "event"      -> TypeEvent
    "ai_conv"    -> TypeAiConv
    "liked"      -> TypeLiked
    else         -> Color(0xFF94A3B8)
}

fun entryTypeIcon(type: String): String = when (type) {
    "thought"    -> "💭"
    "definition" -> "📖"
    "webpage"    -> "🌐"
    "book"       -> "📚"
    "video"      -> "🎬"
    "document"   -> "📄"
    "media"      -> "🖼️"
    "person"     -> "👤"
    "org"        -> "🏢"
    "place"      -> "📍"
    "event"      -> "📅"
    "liked"      -> "❤️"
    "ai_conv"    -> "🤖"
    else         -> "📝"
}

fun entryTypeLabelJa(type: String): String = when (type) {
    "thought"    -> "メモ"
    "definition" -> "単語帳"
    "webpage"    -> "Webページ"
    "book"       -> "本"
    "video"      -> "動画"
    "document"   -> "ドキュメント"
    "media"      -> "メディア"
    "person"     -> "人物"
    "org"        -> "組織"
    "place"      -> "場所"
    "event"      -> "出来事"
    "liked"      -> "いいね"
    "ai_conv"    -> "AI会話"
    else         -> type
}
```

### `app/src/main/java/com/example/encyclopedia/ui/theme/Theme.kt`
```kotlin
package com.example.encyclopedia.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun EncyclopediaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
```

---

## 7. ViewModel

### `app/src/main/java/com/example/encyclopedia/viewmodel/DashboardViewModel.kt`
```kotlin
package com.example.encyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.encyclopedia.db.entity.EntryEntity
import com.example.encyclopedia.repository.EntryRepository
import com.example.encyclopedia.repository.ThoughtDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: EntryRepository
) : ViewModel() {

    val recentEntries: StateFlow<List<EntryEntity>> =
        repo.observeRecent(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> =
        repo.observeCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _quickAddTitle = MutableStateFlow("")
    val quickAddTitle: StateFlow<String> = _quickAddTitle

    fun onQuickAddTitleChange(value: String) { _quickAddTitle.value = value }

    fun quickAddThought() {
        val title = _quickAddTitle.value.trim()
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.createThought(ThoughtDraft(title = title, content = null))
            _quickAddTitle.value = ""
        }
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repo.toggleFavorite(id) }
    }

    fun softDelete(id: String) {
        viewModelScope.launch { repo.softDelete(id) }
    }
}
```

### `app/src/main/java/com/example/encyclopedia/viewmodel/SearchViewModel.kt`
```kotlin
package com.example.encyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.encyclopedia.db.entity.EntryEntity
import com.example.encyclopedia.repository.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: EntryRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _typeFilter = MutableStateFlow<String?>(null)
    val typeFilter: StateFlow<String?> = _typeFilter

    val results: StateFlow<List<EntryEntity>> = _query
        .debounce(300)
        .combine(_typeFilter) { q, t -> q to t }
        .flatMapLatest { (q, typeFilter) ->
            when {
                q.isBlank() && typeFilter == null -> repo.observeAll()
                q.isBlank() && typeFilter != null -> repo.observeByType(typeFilter)
                else -> repo.search(q)
                    .map { entries ->
                        if (typeFilter != null) entries.filter { it.type == typeFilter }
                        else entries
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(value: String) { _query.value = value }
    fun setTypeFilter(type: String?) { _typeFilter.value = type }
}
```

### `app/src/main/java/com/example/encyclopedia/viewmodel/ThoughtEditViewModel.kt`
```kotlin
package com.example.encyclopedia.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.encyclopedia.repository.EntryRepository
import com.example.encyclopedia.repository.ThoughtDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThoughtEditViewModel @Inject constructor(
    private val repo: EntryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val entryId: String? = savedStateHandle["entryId"]
    val isNew: Boolean = entryId == null

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    private val _mood = MutableStateFlow("")
    val mood: StateFlow<String> = _mood

    private val _saved = MutableSharedFlow<String>()
    val saved: SharedFlow<String> = _saved

    init {
        if (entryId != null) {
            viewModelScope.launch {
                repo.observeEntry(entryId).first()?.let { entry ->
                    _title.value = entry.title
                    _content.value = entry.content ?: ""
                }
                repo.observeThought(entryId).first()?.let { thought ->
                    _mood.value = thought.mood ?: ""
                }
            }
        }
    }

    fun onTitleChange(v: String) { _title.value = v }
    fun onContentChange(v: String) { _content.value = v }
    fun onMoodChange(v: String) { _mood.value = v }

    fun save() {
        val titleVal = _title.value.trim()
        if (titleVal.isBlank()) return
        viewModelScope.launch {
            val draft = ThoughtDraft(
                title = titleVal,
                content = _content.value.takeIf { it.isNotBlank() },
                mood = _mood.value.takeIf { it.isNotBlank() }
            )
            if (isNew) {
                val id = repo.createThought(draft)
                _saved.emit(id)
            } else {
                repo.updateThought(entryId!!, draft)
                _saved.emit(entryId)
            }
        }
    }
}
```

### `app/src/main/java/com/example/encyclopedia/viewmodel/DefinitionEditViewModel.kt`
```kotlin
package com.example.encyclopedia.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.encyclopedia.repository.DefinitionDraft
import com.example.encyclopedia.repository.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DefinitionEditViewModel @Inject constructor(
    private val repo: EntryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val entryId: String? = savedStateHandle["entryId"]
    val isNew: Boolean = entryId == null

    private val _term = MutableStateFlow("")
    val term: StateFlow<String> = _term

    private val _reading = MutableStateFlow("")
    val reading: StateFlow<String> = _reading

    private val _definition = MutableStateFlow("")
    val definition: StateFlow<String> = _definition

    private val _field = MutableStateFlow("")
    val field: StateFlow<String> = _field

    private val _saved = MutableSharedFlow<String>()
    val saved: SharedFlow<String> = _saved

    init {
        if (entryId != null) {
            viewModelScope.launch {
                repo.observeDefinition(entryId).first()?.let { def ->
                    _term.value = def.term
                    _reading.value = def.reading ?: ""
                    _definition.value = def.definition
                    _field.value = def.field ?: ""
                }
            }
        }
    }

    fun onTermChange(v: String) { _term.value = v }
    fun onReadingChange(v: String) { _reading.value = v }
    fun onDefinitionChange(v: String) { _definition.value = v }
    fun onFieldChange(v: String) { _field.value = v }

    fun save() {
        val termVal = _term.value.trim()
        val defVal = _definition.value.trim()
        if (termVal.isBlank() || defVal.isBlank()) return
        viewModelScope.launch {
            val draft = DefinitionDraft(
                term = termVal,
                reading = _reading.value.takeIf { it.isNotBlank() },
                definition = defVal,
                field = _field.value.takeIf { it.isNotBlank() }
            )
            if (isNew) {
                val id = repo.createDefinition(draft)
                _saved.emit(id)
            } else {
                repo.updateDefinition(entryId!!, draft)
                _saved.emit(entryId)
            }
        }
    }
}
```

### `app/src/main/java/com/example/encyclopedia/viewmodel/EntryDetailViewModel.kt`
```kotlin
package com.example.encyclopedia.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.encyclopedia.db.entity.EntryDefinitionEntity
import com.example.encyclopedia.db.entity.EntryEntity
import com.example.encyclopedia.db.entity.EntryThoughtEntity
import com.example.encyclopedia.db.entity.TagEntity
import com.example.encyclopedia.repository.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    private val repo: EntryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val entryId: String = savedStateHandle["entryId"] ?: ""

    val entry: StateFlow<EntryEntity?> =
        repo.observeEntry(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val thought: StateFlow<EntryThoughtEntity?> =
        repo.observeThought(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val definition: StateFlow<EntryDefinitionEntity?> =
        repo.observeDefinition(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tags: StateFlow<List<TagEntity>> =
        repo.observeTagsForEntry(entryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch { repo.touch(entryId) }
    }

    fun toggleFavorite() {
        viewModelScope.launch { repo.toggleFavorite(entryId) }
    }

    fun softDelete() {
        viewModelScope.launch { repo.softDelete(entryId) }
    }

    fun addTag(tagName: String) {
        if (tagName.isBlank()) return
        viewModelScope.launch { repo.addTag(entryId, tagName.trim()) }
    }

    fun removeTag(tagId: String) {
        viewModelScope.launch { repo.removeTag(entryId, tagId) }
    }
}
```

### `app/src/main/java/com/example/encyclopedia/viewmodel/ServerViewModel.kt`
```kotlin
package com.example.encyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.encyclopedia.db.dao.EntryDao
import com.example.encyclopedia.db.dao.EntryDefinitionDao
import com.example.encyclopedia.db.dao.EntryThoughtDao
import com.example.encyclopedia.server.LocalServer
import com.example.encyclopedia.server.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val localServer: LocalServer,
    private val tokenManager: TokenManager,
    private val entryDao: EntryDao,
    private val thoughtDao: EntryThoughtDao,
    private val definitionDao: EntryDefinitionDao
) : ViewModel() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    val token: StateFlow<String?> = tokenManager.tokenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch { tokenManager.getOrCreateToken() }
    }

    fun toggleServer() {
        viewModelScope.launch {
            if (_isRunning.value) {
                localServer.stop()
                _isRunning.value = false
            } else {
                localServer.entryDao = entryDao
                localServer.thoughtDao = thoughtDao
                localServer.definitionDao = definitionDao
                localServer.start()
                _isRunning.value = true
            }
        }
    }

    fun regenerateToken() {
        viewModelScope.launch { tokenManager.regenerateToken() }
    }

    override fun onCleared() {
        super.onCleared()
        localServer.stop()
    }
}
```

---

## 8. UIコンポーネント

### `app/src/main/java/com/example/encyclopedia/ui/component/EntryCard.kt`
```kotlin
package com.example.encyclopedia.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.encyclopedia.db.entity.EntryEntity
import com.example.encyclopedia.ui.theme.entryTypeColor
import com.example.encyclopedia.ui.theme.entryTypeIcon
import com.example.encyclopedia.ui.theme.entryTypeLabelJa
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun EntryCard(
    entry: EntryEntity,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Type badge
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(entryTypeColor(entry.type).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entryTypeIcon(entry.type),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.content.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entry.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                entryTypeLabelJa(entry.type),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDate(entry.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onFavoriteClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (entry.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = "お気に入り",
                    tint = if (entry.isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
```

### `app/src/main/java/com/example/encyclopedia/ui/component/EmptyState.kt`
```kotlin
package com.example.encyclopedia.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = emoji, fontSize = 48.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
```

---

## 9. 画面

### `app/src/main/java/com/example/encyclopedia/ui/screen/DashboardScreen.kt`
```kotlin
package com.example.encyclopedia.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.encyclopedia.ui.component.EmptyState
import com.example.encyclopedia.ui.component.EntryCard
import com.example.encyclopedia.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToEntry: (String) -> Unit,
    onNavigateToNewThought: () -> Unit,
    onNavigateToNewDefinition: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val recentEntries by viewModel.recentEntries.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val quickAddTitle by viewModel.quickAddTitle.collectAsState()

    var showQuickAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personal Encyclopedia") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showQuickAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "追加")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quick add
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quickAddTitle,
                            onValueChange = viewModel::onQuickAddTitleChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("何か思いついたら...") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = viewModel::quickAddThought,
                            enabled = quickAddTitle.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "保存")
                        }
                    }
                }
            }

            // Stats
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatItem("合計", "$totalCount 件")
                        StatItem("最近", "${recentEntries.size} 件")
                    }
                }
            }

            // Section header
            item {
                Text(
                    text = "最近の追加",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Entries
            if (recentEntries.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "📝",
                        title = "まだエントリーがありません",
                        subtitle = "上のフォームから最初のメモを追加しましょう"
                    )
                }
            } else {
                items(recentEntries, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onClick = { onNavigateToEntry(entry.id) },
                        onFavoriteClick = { viewModel.toggleFavorite(entry.id) }
                    )
                }
            }
        }
    }

    // Quick Add Dialog
    if (showQuickAddDialog) {
        AlertDialog(
            onDismissRequest = { showQuickAddDialog = false },
            title = { Text("新規追加") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            showQuickAddDialog = false
                            onNavigateToNewThought()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("💭 メモを書く")
                    }
                    FilledTonalButton(
                        onClick = {
                            showQuickAddDialog = false
                            onNavigateToNewDefinition()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📖 単語帳に追加")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQuickAddDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
```

### `app/src/main/java/com/example/encyclopedia/ui/screen/SearchScreen.kt`
```kotlin
package com.example.encyclopedia.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.encyclopedia.ui.component.EmptyState
import com.example.encyclopedia.ui.component.EntryCard
import com.example.encyclopedia.ui.theme.entryTypeLabelJa
import com.example.encyclopedia.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onNavigateToEntry: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()

    val types = listOf(
        null to "すべて",
        "thought" to "メモ",
        "definition" to "単語帳"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("キーワードを検索…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "クリア")
                                }
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Type filter chips
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                types.forEach { (type, label) ->
                    FilterChip(
                        selected = typeFilter == type,
                        onClick = { viewModel.setTypeFilter(type) },
                        label = { Text(label) }
                    )
                }
            }

            // Results
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (results.isEmpty()) {
                    item {
                        EmptyState(
                            emoji = "🔍",
                            title = if (query.isBlank()) "エントリーがありません" else "「$query」に一致する結果がありません"
                        )
                    }
                }
                items(results, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onClick = { onNavigateToEntry(entry.id) },
                        onFavoriteClick = {}
                    )
                }
            }
        }
    }
}
```

### `app/src/main/java/com/example/encyclopedia/ui/screen/ThoughtEditScreen.kt`
```kotlin
package com.example.encyclopedia.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.encyclopedia.viewmodel.ThoughtEditViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThoughtEditScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ThoughtEditViewModel = hiltViewModel()
) {
    val title by viewModel.title.collectAsState()
    val content by viewModel.content.collectAsState()
    val mood by viewModel.mood.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.saved.collectLatest { id -> onSaved(id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "新しいメモ" else "メモを編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = title.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("タイトル *") },
                singleLine = true
            )
            OutlinedTextField(
                value = content,
                onValueChange = viewModel::onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("内容") },
                minLines = 5
            )
            OutlinedTextField(
                value = mood,
                onValueChange = viewModel::onMoodChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("気分（任意）") },
                singleLine = true,
                supportingText = { Text("例: 😊 やる気あり、🤔 考え中") }
            )
        }
    }
}
```

### `app/src/main/java/com/example/encyclopedia/ui/screen/DefinitionEditScreen.kt`
```kotlin
package com.example.encyclopedia.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.encyclopedia.viewmodel.DefinitionEditViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefinitionEditScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: DefinitionEditViewModel = hiltViewModel()
) {
    val term by viewModel.term.collectAsState()
    val reading by viewModel.reading.collectAsState()
    val definition by viewModel.definition.collectAsState()
    val field by viewModel.field.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.saved.collectLatest { id -> onSaved(id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "単語帳に追加" else "単語を編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = term.isNotBlank() && definition.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = term,
                onValueChange = viewModel::onTermChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("用語 *") },
                singleLine = true
            )
            OutlinedTextField(
                value = reading,
                onValueChange = viewModel::onReadingChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("読み（任意）") },
                singleLine = true
            )
            OutlinedTextField(
                value = definition,
                onValueChange = viewModel::onDefinitionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("定義 *") },
                minLines = 3
            )
            OutlinedTextField(
                value = field,
                onValueChange = viewModel::onFieldChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("分野（任意）") },
                singleLine = true,
                supportingText = { Text("例: 数学, CS, 歴史") }
            )
        }
    }
}
```

### `app/src/main/java/com/example/encyclopedia/ui/screen/EntryDetailScreen.kt`
```kotlin
package com.example.encyclopedia.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.encyclopedia.ui.theme.entryTypeColor
import com.example.encyclopedia.ui.theme.entryTypeIcon
import com.example.encyclopedia.ui.theme.entryTypeLabelJa
import com.example.encyclopedia.viewmodel.EntryDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryDetailScreen(
    onBack: () -> Unit,
    onEdit: (type: String, entryId: String) -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel()
) {
    val entry by viewModel.entry.collectAsState()
    val thought by viewModel.thought.collectAsState()
    val definition by viewModel.definition.collectAsState()
    val tags by viewModel.tags.collectAsState()

    var showTagDialog by remember { mutableStateOf(false) }
    var tagInput by remember { mutableStateOf("") }

    val e = entry
    if (e == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entryTypeLabelJa(e.type)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (e.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "お気に入り",
                            tint = if (e.isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onEdit(e.type, e.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "編集")
                    }
                    IconButton(onClick = {
                        viewModel.softDelete()
                        onBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Type badge + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(entryTypeColor(e.type).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entryTypeIcon(e.type),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = e.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
            }

            // Metadata
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            Text(
                text = "作成: ${sdf.format(Date(e.createdAt))} | 更新: ${sdf.format(Date(e.updatedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Content (user notes)
            if (!e.content.isNullOrBlank()) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📝 メモ", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(e.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Type-specific sections
            when (e.type) {
                "thought" -> thought?.let { t ->
                    if (!t.mood.isNullOrBlank()) {
                        Row {
                            Text("気分: ", style = MaterialTheme.typography.labelMedium)
                            Text(t.mood, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!t.context.isNullOrBlank()) {
                        Row {
                            Text("文脈: ", style = MaterialTheme.typography.labelMedium)
                            Text(t.context, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                "definition" -> definition?.let { d ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = entryTypeColor("definition").copy(alpha = 0.08f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = d.term,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            if (!d.reading.isNullOrBlank()) {
                                Text(
                                    text = d.reading,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!d.field.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(d.field) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            Text(
                                text = d.definition,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // Tags
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏷️ タグ", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { showTagDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "タグを追加",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (tags.isEmpty()) {
                        Text(
                            "タグなし",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tags.forEach { tag ->
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text(tag.name) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { viewModel.removeTag(tag.id) },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "削除",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add tag dialog
    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("タグを追加") },
            text = {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("タグ名") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addTag(tagInput)
                        tagInput = ""
                        showTagDialog = false
                    },
                    enabled = tagInput.isNotBlank()
                ) { Text("追加") }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text("キャンセル") }
            }
        )
    }
}
```

### `app/src/main/java/com/example/encyclopedia/ui/screen/SettingsScreen.kt`
```kotlin
package com.example.encyclopedia.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.encyclopedia.viewmodel.ServerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel()
) {
    val isRunning by viewModel.isRunning.collectAsState()
    val token by viewModel.token.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server section
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🌐 Ktorローカルサーバー", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRunning) "サーバー稼働中" else "サーバー停止中",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (isRunning) {
                                Text(
                                    text = "ポート: 8080",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isRunning,
                            onCheckedChange = { viewModel.toggleServer() }
                        )
                    }
                }
            }

            // Token section
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔑 アクセストークン", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = token ?: "生成中...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            token?.let {
                                clipboardManager.setText(AnnotatedString(it))
                                Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("コピー")
                        }
                        OutlinedButton(onClick = { viewModel.regenerateToken() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("再生成")
                        }
                    }
                }
            }

            // App info
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ℹ️ アプリ情報", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Personal Encyclopedia v0.1.0", style = MaterialTheme.typography.bodyMedium)
                    Text("Phase 0 — 毎日使える最小版", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "データはすべてこの端末内のSQLiteデータベースに保存されています。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
```

---

## 10. ナビゲーション

### `app/src/main/java/com/example/encyclopedia/ui/navigation/NavGraph.kt`
```kotlin
package com.example.encyclopedia.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.encyclopedia.ui.screen.*

object Routes {
    const val DASHBOARD = "dashboard"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val THOUGHT_NEW = "thought/new"
    const val THOUGHT_EDIT = "thought/edit/{entryId}"
    const val DEFINITION_NEW = "definition/new"
    const val DEFINITION_EDIT = "definition/edit/{entryId}"
    const val ENTRY_DETAIL = "entry/{entryId}"
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToEntry = { id -> navController.navigate("entry/$id") },
                onNavigateToNewThought = { navController.navigate(Routes.THOUGHT_NEW) },
                onNavigateToNewDefinition = { navController.navigate(Routes.DEFINITION_NEW) },
                onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEntry = { id -> navController.navigate("entry/$id") }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.THOUGHT_NEW) {
            ThoughtEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate("entry/$id")
                }
            )
        }

        composable(
            route = Routes.THOUGHT_EDIT,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) {
            ThoughtEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(Routes.DEFINITION_NEW) {
            DefinitionEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate("entry/$id")
                }
            )
        }

        composable(
            route = Routes.DEFINITION_EDIT,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) {
            DefinitionEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ENTRY_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) {
            EntryDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { type, entryId ->
                    when (type) {
                        "thought" -> navController.navigate("thought/edit/$entryId")
                        "definition" -> navController.navigate("definition/edit/$entryId")
                    }
                }
            )
        }
    }
}
```

---

## 11. アプリケーション / MainActivity

### `app/src/main/java/com/example/encyclopedia/PersonalEncyclopediaApp.kt`
```kotlin
package com.example.encyclopedia

import android.app.Application
import com.example.encyclopedia.db.AppDatabase
import com.example.encyclopedia.db.SeedData
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PersonalEncyclopediaApp : Application() {

    @Inject lateinit var database: AppDatabase

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            database.entryTypeDao().insertAll(SeedData.entryTypes)
        }
    }
}
```

### `app/src/main/java/com/example/encyclopedia/MainActivity.kt`
```kotlin
package com.example.encyclopedia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.encyclopedia.ui.navigation.AppNavGraph
import com.example.encyclopedia.ui.navigation.Routes
import com.example.encyclopedia.ui.theme.EncyclopediaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EncyclopediaTheme {
                MainContent()
            }
        }
    }
}

@Composable
private fun MainContent() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    // Bottom bar visible only on top-level screens
    val showBottomBar = currentRoute in listOf(Routes.DASHBOARD, Routes.SEARCH)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("ホーム") },
                        selected = currentRoute == Routes.DASHBOARD,
                        onClick = {
                            if (currentRoute != Routes.DASHBOARD) {
                                navController.navigate(Routes.DASHBOARD) {
                                    popUpTo(Routes.DASHBOARD) { inclusive = true }
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("検索") },
                        selected = currentRoute == Routes.SEARCH,
                        onClick = {
                            if (currentRoute != Routes.SEARCH) {
                                navController.navigate(Routes.SEARCH) {
                                    popUpTo(Routes.DASHBOARD)
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            AppNavGraph(navController = navController)
        }
    }
}
```

---

## 12. AndroidManifest

### `app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

    <application
        android:name=".PersonalEncyclopediaApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Encyclopedia"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Encyclopedia"
        tools:targetApi="34">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Encyclopedia"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```
### `app/src/main/res/values/themes.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Compose が全て描画するため、Activity のテーマは非常に簡素でOK。
         Material Components への依存を発生させないため、
         AppCompat/DayNight 系の親を参照しない。 -->
    <style name="Theme.Encyclopedia" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:windowBackground">@android:color/white</item>
        <item name="android:windowLightStatusBar">true</item>
    </style>
</resources>
```
### `app/src/main/res/values-night/themes.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Encyclopedia" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:windowBackground">@android:color/black</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
</resources>
```

---

## 13. proguard-rules.pro

### `app/proguard-rules.pro`
```
# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt
-keep class dagger.hilt.** { *; }
```

phase0の時点で動作がゆっくりだなという印象を受けた。改善していかないといけない。
**次のステップ(フェーズ1)**: `srs_review` + SM-2 + `quiz_bank` + `quiz_attempts` + 多段階採点 + Drive日次バックアップ + CSV/MDインポート。