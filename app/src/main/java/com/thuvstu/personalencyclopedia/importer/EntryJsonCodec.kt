package com.thuvstu.personalencyclopedia.importer

import com.thuvstu.personalencyclopedia.db.entity.*
import kotlinx.serialization.json.*

/**
 * ★往復対称(mismatch §6-2 / v15 §14「Room＋可搬exportの二重保証」):
 * `EntryExporter.buildJson` が書いた1要素を、entry本体＋型別拡張＋タグ名に**欠落なく**復元する純粋関数群。
 * DBに触れないのでJVMテスト可能(`EntryJsonCodecTest`)。
 *
 * 方針:
 * - 旧ID・createdAt/updatedAt/accessedAt・isFavorite/isMuted・lang・metadataJson を保持する
 *   (`keepId=false` の時だけ新IDを採番。同一端末への再取込で衝突する場合に使う)。
 * - 拡張は11型すべて。必須カラムが欠けている場合はその型のデフォルト(旧exportとの後方互換)で埋める。
 * - 未知のキーは無視、既知のキーの型不一致は例外にせず null 扱い(1件の破損で全体を止めない)。
 */
object EntryJsonCodec {

    data class Decoded(
        val entry: EntryEntity,
        val tags: List<String>,
        val thought: EntryThoughtEntity? = null,
        val definition: EntryDefinitionEntity? = null,
        val webpage: EntryWebpageEntity? = null,
        val book: EntryBookEntity? = null,
        val video: EntryVideoEntity? = null,
        val document: EntryDocumentEntity? = null,
        val media: EntryMediaEntity? = null,
        val person: EntryPersonEntity? = null,
        val org: EntryOrgEntity? = null,
        val place: EntryPlaceEntity? = null,
        val event: EntryEventEntity? = null,
        val liked: EntryLikedEntity? = null,
        val aiConv: EntryAiConvEntity? = null,
        /** ★wt56 付箋。entryId は復元後の id に付け替え済み */
        val stickyNotes: List<EntryStickyNoteEntity> = emptyList()
    )

    /** タイトルが無い要素は復元不能として null を返す */
    fun decode(obj: JsonObject, keepId: Boolean, newId: String, now: Long): Decoded? {
        val title = obj.str("title") ?: return null
        val type = obj.str("type") ?: "thought"
        val id = if (keepId) (obj.str("id") ?: newId) else newId
        val createdAt = obj.long("createdAt") ?: now
        val updatedAt = obj.long("updatedAt") ?: createdAt

        val entry = EntryEntity(
            id = id,
            type = type,
            title = title,
            content = obj.str("content"),
            summary = obj.str("summary"),
            sourceUrl = obj.str("sourceUrl"),
            lang = obj.str("lang"),
            isFavorite = obj.bool("isFavorite") ?: false,
            isMuted = obj.bool("isMuted") ?: false,
            accessedAt = obj.long("accessedAt"),
            metadataJson = obj.str("metadataJson") ?: "{}",
            createdAt = createdAt,
            updatedAt = updatedAt
        )
        val tags = obj["tags"]?.let { el ->
            (el as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { t -> t.isNotEmpty() } }
        } ?: emptyList()

        // ★wt56 付箋: 旧export(キー無し)は空。1枚の破損で全体を止めない
        val stickyNotes = (obj["stickyNotes"] as? JsonArray)?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val text = o.str("text")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            EntryStickyNoteEntity(
                id = if (keepId) (o.str("id") ?: java.util.UUID.randomUUID().toString()) else java.util.UUID.randomUUID().toString(),
                entryId = id,
                text = text,
                color = o.str("color") ?: "yellow",
                source = o.str("source") ?: "detail",
                contextId = o.str("contextId"),
                isPinned = o.bool("isPinned") ?: false,
                isResolved = o.bool("isResolved") ?: false,
                resolvedAt = o.long("resolvedAt"),
                sortOrder = o.int("sortOrder") ?: 0,
                // 昇格先IDは別端末では意味を持たないため keepId のときだけ引き継ぐ
                promotedEntryId = if (keepId) o.str("promotedEntryId") else null,
                promotedTaskId = if (keepId) o.str("promotedTaskId") else null,
                promotedCandidateId = if (keepId) o.str("promotedCandidateId") else null,
                createdAt = o.long("createdAt") ?: now,
                updatedAt = o.long("updatedAt") ?: (o.long("createdAt") ?: now)
            )
        } ?: emptyList()

        val ext = obj["extension"] as? JsonObject
        var d = Decoded(entry = entry, tags = tags, stickyNotes = stickyNotes)
        when (type) {
            "thought" -> d = d.copy(
                thought = EntryThoughtEntity(
                    entryId = id,
                    mood = ext?.str("mood"),
                    context = ext?.str("context") ?: "json_import",
                    isDraft = ext?.bool("isDraft") ?: false
                )
            )
            "definition" -> {
                val definition = ext?.str("definition")
                if (!definition.isNullOrBlank()) d = d.copy(
                    definition = EntryDefinitionEntity(
                        entryId = id,
                        term = ext.str("term") ?: title,
                        reading = ext.str("reading"),
                        definition = definition,
                        field = ext.str("field"),
                        examplesJson = ext.str("examplesJson") ?: "[]",
                        relatedTermsJson = ext.str("relatedTermsJson") ?: "[]"
                    )
                )
            }
            "webpage" -> {
                val url = ext?.str("url") ?: entry.sourceUrl
                if (!url.isNullOrBlank()) d = d.copy(
                    webpage = EntryWebpageEntity(
                        entryId = id,
                        url = url,
                        domain = ext?.str("domain") ?: domainOf(url),
                        scrapedAt = ext?.long("scrapedAt"),
                        fullText = ext?.str("fullText"),
                        readingTimeS = ext?.int("readingTimeS"),
                        author = ext?.str("author"),
                        publishedAt = ext?.long("publishedAt"),
                        scraperUsed = ext?.str("scraperUsed")
                    )
                )
            }
            "book" -> if (ext != null) d = d.copy(
                book = EntryBookEntity(
                    entryId = id,
                    isbn = ext.str("isbn"),
                    authorsJson = ext.str("authorsJson") ?: "[]",
                    publisher = ext.str("publisher"),
                    publishedYear = ext.int("publishedYear"),
                    totalPages = ext.int("totalPages"),
                    readStatus = ext.str("readStatus") ?: "unread",
                    readStartDate = ext.long("readStartDate"),
                    readEndDate = ext.long("readEndDate"),
                    rating = ext.int("rating")
                )
            )
            "video" -> if (ext != null) d = d.copy(
                video = EntryVideoEntity(
                    entryId = id,
                    platform = ext.str("platform") ?: "unknown",
                    videoId = ext.str("videoId"),
                    channelName = ext.str("channelName"),
                    durationS = ext.int("durationS"),
                    thumbnailUrl = ext.str("thumbnailUrl"),
                    transcript = ext.str("transcript"),
                    watchedAt = ext.long("watchedAt"),
                    watchProgress = ext.float("watchProgress")
                )
            )
            "document" -> if (ext != null) d = d.copy(
                document = EntryDocumentEntity(
                    entryId = id,
                    docType = ext.str("docType") ?: "other",
                    mimeType = ext.str("mimeType") ?: "application/octet-stream",
                    fileSizeBytes = ext.long("fileSizeBytes"),
                    pageCount = ext.int("pageCount"),
                    extractedText = ext.str("extractedText"),
                    extractionMethod = ext.str("extractionMethod")
                )
            )
            "media" -> {
                // blobPath は端末固有パス。無ければ復元しない(実体ファイルが無いので)
                val blob = ext?.str("blobPath")
                if (!blob.isNullOrBlank()) d = d.copy(
                    media = EntryMediaEntity(
                        entryId = id,
                        mediaType = ext.str("mediaType") ?: "image",
                        blobPath = blob,
                        mimeType = ext.str("mimeType") ?: "application/octet-stream",
                        widthPx = ext.int("widthPx"),
                        heightPx = ext.int("heightPx"),
                        durationS = ext.float("durationS"),
                        ocrText = ext.str("ocrText"),
                        caption = ext.str("caption")
                    )
                )
            }
            "person" -> if (ext != null) d = d.copy(
                person = EntryPersonEntity(
                    entryId = id,
                    fullName = ext.str("fullName") ?: title,
                    aliasesJson = ext.str("aliasesJson") ?: "[]",
                    birthYear = ext.int("birthYear"),
                    deathYear = ext.int("deathYear"),
                    nationality = ext.str("nationality"),
                    occupationsJson = ext.str("occupationsJson") ?: "[]",
                    biography = ext.str("biography")
                )
            )
            "org" -> if (ext != null) d = d.copy(
                org = EntryOrgEntity(
                    entryId = id,
                    officialName = ext.str("officialName") ?: title,
                    orgType = ext.str("orgType"),
                    foundedYear = ext.int("foundedYear"),
                    country = ext.str("country"),
                    websiteUrl = ext.str("websiteUrl"),
                    description = ext.str("description")
                )
            )
            "place" -> if (ext != null) d = d.copy(
                place = EntryPlaceEntity(
                    entryId = id,
                    placeName = ext.str("placeName") ?: title,
                    placeType = ext.str("placeType"),
                    address = ext.str("address"),
                    latitude = ext.double("latitude"),
                    longitude = ext.double("longitude"),
                    visitedDatesJson = ext.str("visitedDatesJson") ?: "[]"
                )
            )
            "event" -> if (ext != null) d = d.copy(
                event = EntryEventEntity(
                    entryId = id,
                    eventName = ext.str("eventName") ?: title,
                    startedAt = ext.long("startedAt") ?: createdAt,
                    endedAt = ext.long("endedAt"),
                    locationText = ext.str("locationText"),
                    placeEntryId = ext.str("placeEntryId"),
                    isPersonal = ext.bool("isPersonal") ?: true,
                    participantsJson = ext.str("participantsJson") ?: "[]"
                )
            )
            "liked" -> if (ext != null) d = d.copy(
                liked = EntryLikedEntity(
                    entryId = id,
                    platform = ext.str("platform") ?: "unknown",
                    originalId = ext.str("originalId") ?: id,
                    likedAt = ext.long("likedAt"),
                    contentType = ext.str("contentType") ?: "post",
                    authorName = ext.str("authorName"),
                    fullText = ext.str("fullText")
                )
            )
            "ai_conv" -> if (ext != null) d = d.copy(
                aiConv = EntryAiConvEntity(
                    entryId = id,
                    model = ext.str("model") ?: "unknown",
                    provider = ext.str("provider") ?: "unknown",
                    messagesJson = ext.str("messagesJson") ?: "[]",
                    tokenCount = ext.int("tokenCount"),
                    topic = ext.str("topic"),
                    isUseful = ext.bool("isUseful")
                )
            )
        }
        return d
    }

    // ── 寛容な取り出し(型不一致は null) ──

    private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
    private fun JsonObject.str(key: String): String? = prim(key)?.let { if (it.isString) it.content else it.contentOrNull }
    private fun JsonObject.long(key: String): Long? = prim(key)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun JsonObject.int(key: String): Int? = prim(key)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    private fun JsonObject.float(key: String): Float? = prim(key)?.floatOrNull
    private fun JsonObject.double(key: String): Double? = prim(key)?.doubleOrNull
    private fun JsonObject.bool(key: String): Boolean? = prim(key)?.let {
        it.booleanOrNull ?: when (it.contentOrNull) { "1" -> true; "0" -> false; else -> null }
    }

    private fun domainOf(url: String): String = try {
        java.net.URI(url).host?.removePrefix("www.") ?: ""
    } catch (_: Exception) { "" }
}
