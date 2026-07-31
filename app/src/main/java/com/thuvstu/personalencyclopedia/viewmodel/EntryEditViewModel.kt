package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thuvstu.personalencyclopedia.db.entity.*
import com.thuvstu.personalencyclopedia.repository.DefinitionDraft
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import com.thuvstu.personalencyclopedia.repository.ThoughtDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


/** 全13 Entry Type をカバーする統合フォーム状態(§5.4)。未使用フィールドは既定値のまま。 */
data class EntryFormState(
    // 共通
    val title: String = "",
    val content: String = "",
    // thought
    val mood: String = "",
    // definition
    val term: String = "",
    val reading: String = "",
    val definition: String = "",
    val field: String = "",
    // webpage
    val url: String = "",
    val author: String = "",
    val fullText: String = "",
    // book
    val isbn: String = "",
    val authorsText: String = "",
    val publisher: String = "",
    val publishedYear: String = "",
    val totalPages: String = "",
    val readStatus: String = "unread",
    val rating: Int = 0,
    // video
    val platform: String = "",
    val channelName: String = "",
    val durationS: String = "",
    val transcript: String = "",
    // document
    val docType: String = "txt",
    val fileSizeBytes: String = "",
    val pageCount: String = "",
    val extractedText: String = "",
    // media
    val mediaType: String = "image",
    val caption: String = "",
    val ocrText: String = "",
    // person
    val fullName: String = "",
    val aliasesText: String = "",
    val birthYear: String = "",
    val deathYear: String = "",
    val nationality: String = "",
    val occupationsText: String = "",
    val biography: String = "",
    // org
    val officialName: String = "",
    val orgType: String = "",
    val foundedYear: String = "",
    val country: String = "",
    val websiteUrl: String = "",
    val description: String = "",
    // place
    val placeName: String = "",
    val placeType: String = "",
    val address: String = "",
    val latitude: String = "",
    val longitude: String = "",
    // event
    val eventName: String = "",
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val locationText: String = "",
    val participantsText: String = "",
    // liked
    val likedPlatform: String = "",
    val originalId: String = "",
    val likedContentType: String = "",
    val likedAuthorName: String = "",
    val likedFullText: String = "",
    // ai_conv
    val aiModel: String = "",
    val aiProvider: String = "google",
    val aiTopic: String = "",
    val aiMessagesText: String = ""
)

@HiltViewModel
class EntryEditViewModel @Inject constructor(
    private val repo: EntryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val entryType: String = savedStateHandle.get<String>("type") ?: "thought"
    private val entryId: String? = savedStateHandle.get<String>("entryId")
    val isNew: Boolean = entryId == null

    private val _form = MutableStateFlow(EntryFormState())
    val form: StateFlow<EntryFormState> = _form

    private val _saved = MutableSharedFlow<String>()
    val saved: SharedFlow<String> = _saved

    init {
        val id = entryId
        if (id != null) {
            viewModelScope.launch {
                val entry = repo.getEntry(id) ?: return@launch
                val base = EntryFormState(title = entry.title, content = entry.content ?: "")
                _form.value = when (entryType) {
                    "thought" -> repo.observeThought(id).first()
                        ?.let { base.copy(mood = it.mood ?: "") } ?: base
                    "definition" -> repo.observeDefinition(id).first()?.let {
                        base.copy(term = it.term, reading = it.reading ?: "",
                            definition = it.definition, field = it.field ?: "")
                    } ?: base
                    "webpage" -> repo.getWebpage(id)?.let {
                        base.copy(url = it.url, author = it.author ?: "", fullText = it.fullText ?: "")
                    } ?: base
                    "book" -> repo.getBook(id)?.let {
                        base.copy(isbn = it.isbn ?: "",
                            authorsText = parseList(it.authorsJson).joinToString(", "),
                            publisher = it.publisher ?: "",
                            publishedYear = it.publishedYear?.toString() ?: "",
                            totalPages = it.totalPages?.toString() ?: "",
                            readStatus = it.readStatus, rating = it.rating ?: 0)
                    } ?: base
                    "video" -> repo.getVideo(id)?.let {
                        base.copy(platform = it.platform, channelName = it.channelName ?: "",
                            durationS = it.durationS?.toString() ?: "", transcript = it.transcript ?: "")
                    } ?: base
                    "document" -> repo.getDocument(id)?.let {
                        base.copy(docType = it.docType,
                            fileSizeBytes = it.fileSizeBytes?.toString() ?: "",
                            pageCount = it.pageCount?.toString() ?: "",
                            extractedText = it.extractedText ?: "")
                    } ?: base
                    "media" -> repo.getMedia(id)?.let {
                        base.copy(mediaType = it.mediaType, caption = it.caption ?: "",
                            ocrText = it.ocrText ?: "")
                    } ?: base
                    "person" -> repo.getPerson(id)?.let {
                        base.copy(fullName = it.fullName,
                            aliasesText = parseList(it.aliasesJson).joinToString(", "),
                            birthYear = it.birthYear?.toString() ?: "",
                            deathYear = it.deathYear?.toString() ?: "",
                            nationality = it.nationality ?: "",
                            occupationsText = parseList(it.occupationsJson).joinToString(", "),
                            biography = it.biography ?: "")
                    } ?: base
                    "org" -> repo.getOrg(id)?.let {
                        base.copy(officialName = it.officialName, orgType = it.orgType ?: "",
                            foundedYear = it.foundedYear?.toString() ?: "", country = it.country ?: "",
                            websiteUrl = it.websiteUrl ?: "", description = it.description ?: "")
                    } ?: base
                    "place" -> repo.getPlace(id)?.let {
                        base.copy(placeName = it.placeName, placeType = it.placeType ?: "",
                            address = it.address ?: "", latitude = it.latitude?.toString() ?: "",
                            longitude = it.longitude?.toString() ?: "")
                    } ?: base
                    "event" -> repo.getEvent(id)?.let {
                        base.copy(eventName = it.eventName, startedAt = it.startedAt,
                            endedAt = it.endedAt, locationText = it.locationText ?: "",
                            participantsText = parseList(it.participantsJson).joinToString(", "))
                    } ?: base
                    "liked" -> repo.getLiked(id)?.let {
                        base.copy(likedPlatform = it.platform, originalId = it.originalId,
                            likedContentType = it.contentType, likedAuthorName = it.authorName ?: "",
                            likedFullText = it.fullText ?: "")
                    } ?: base
                    "ai_conv" -> repo.getAiConv(id)?.let {
                        base.copy(aiModel = it.model, aiProvider = it.provider,
                            aiTopic = it.topic ?: "", aiMessagesText = extractMessagesText(it.messagesJson))
                    } ?: base
                    else -> base
                }
            }
        }
    }

    fun update(transform: (EntryFormState) -> EntryFormState) = _form.update(transform)

    fun save() {
        val f = _form.value
        viewModelScope.launch {
            val id = when (entryType) {
                "thought"    -> saveThought(f)
                "definition" -> saveDefinition(f)
                "webpage"    -> saveWebpage(f)
                "book"       -> saveBook(f)
                "video"      -> saveVideo(f)
                "document"   -> saveDocument(f)
                "media"      -> saveMedia(f)
                "person"     -> savePerson(f)
                "org"        -> saveOrg(f)
                "place"      -> savePlace(f)
                "event"      -> saveEvent(f)
                "liked"      -> saveLiked(f)
                "ai_conv"    -> saveAiConv(f)
                else -> return@launch
            }
            _saved.emit(id)
        }
    }

    // ── 型別保存 ──────────────────────────────────────────────

    private suspend fun saveThought(f: EntryFormState): String {
        val draft = ThoughtDraft(f.title.trim(), f.content.takeIf { it.isNotBlank() },
            mood = f.mood.takeIf { it.isNotBlank() })
        return if (isNew) repo.createThought(draft)
        else { repo.updateThought(entryId!!, draft); entryId }
    }

    private suspend fun saveDefinition(f: EntryFormState): String {
        val draft = DefinitionDraft(f.term.trim(), f.reading.takeIf { it.isNotBlank() },
            f.definition.trim(), field = f.field.takeIf { it.isNotBlank() })
        return if (isNew) repo.createDefinition(draft)
        else { repo.updateDefinition(entryId!!, draft); entryId }
    }

    private suspend fun saveWebpage(f: EntryFormState): String {
        if (isNew) return repo.createWebpage(f.title.trim(), f.content.takeIf { it.isNotBlank() },
            f.url.trim(), f.author.takeIf { it.isNotBlank() }, f.fullText.takeIf { it.isNotBlank() })
        val id = entryId!!; val prev = repo.getWebpage(id)
        repo.updateEntryCommon(id, f.title.trim(), f.content.takeIf { it.isNotBlank() })
        repo.upsertExtension(EntryWebpageEntity(
            entryId = id, url = f.url.trim(), domain = extractDomain(f.url),
            scrapedAt = prev?.scrapedAt ?: System.currentTimeMillis(),
            fullText = f.fullText.takeIf { it.isNotBlank() },
            thumbnailPath = prev?.thumbnailPath,
            readingTimeS = prev?.readingTimeS ?: (f.fullText.length / 400).coerceAtLeast(1),
            author = f.author.takeIf { it.isNotBlank() },
            publishedAt = prev?.publishedAt,
            scraperUsed = prev?.scraperUsed ?: "manual"))
        return id
    }

    private suspend fun saveBook(f: EntryFormState): String {
        val authors = splitList(f.authorsText)
        if (isNew) return repo.createBook(f.title.trim(), f.content.takeIf { it.isNotBlank() },
            f.isbn.takeIf { it.isNotBlank() }, authors, f.publisher.takeIf { it.isNotBlank() },
            f.publishedYear.toIntOrNull(), f.totalPages.toIntOrNull(),
            status = f.readStatus, rating = f.rating.takeIf { it > 0 })
        val id = entryId!!; val prev = repo.getBook(id)
        repo.updateEntryCommon(id, f.title.trim(), f.content.takeIf { it.isNotBlank() })
        repo.upsertExtension(EntryBookEntity(
            entryId = id, isbn = f.isbn.takeIf { it.isNotBlank() },
            authorsJson = toJsonArray(authors),
            publisher = f.publisher.takeIf { it.isNotBlank() },
            publishedYear = f.publishedYear.toIntOrNull(),
            totalPages = f.totalPages.toIntOrNull(),
            readStatus = f.readStatus,
            readStartDate = prev?.readStartDate, readEndDate = prev?.readEndDate,
            rating = f.rating.takeIf { it > 0 }, coverPath = prev?.coverPath))
        return id
    }

    private suspend fun saveVideo(f: EntryFormState): String {
        if (isNew) return repo.createVideo(f.title.trim(), f.content.takeIf { it.isNotBlank() },
            f.platform.ifBlank { "youtube" }, null, f.channelName.takeIf { it.isNotBlank() },
            f.durationS.toIntOrNull(), f.transcript.takeIf { it.isNotBlank() })
        val id = entryId!!; val prev = repo.getVideo(id)
        repo.updateEntryCommon(id, f.title.trim(), f.content.takeIf { it.isNotBlank() })
        repo.upsertExtension(EntryVideoEntity(
            entryId = id, platform = f.platform.ifBlank { "youtube" },
            videoId = prev?.videoId, channelName = f.channelName.takeIf { it.isNotBlank() },
            durationS = f.durationS.toIntOrNull(), thumbnailUrl = prev?.thumbnailUrl,
            transcript = f.transcript.takeIf { it.isNotBlank() },
            watchedAt = prev?.watchedAt, watchProgress = prev?.watchProgress))
        return id
    }

    private suspend fun saveDocument(f: EntryFormState): String {
        if (isNew) return repo.createDocument(f.title.trim(), f.content.takeIf { it.isNotBlank() },
            f.docType, mimeForDoc(f.docType), f.fileSizeBytes.toLongOrNull(),
            f.pageCount.toIntOrNull(), f.extractedText.takeIf { it.isNotBlank() })
        val id = entryId!!; val prev = repo.getDocument(id)
        repo.updateEntryCommon(id, f.title.trim(), f.content.takeIf { it.isNotBlank() })
        repo.upsertExtension(EntryDocumentEntity(
            entryId = id, docType = f.docType, blobPath = prev?.blobPath,
            gdriveId = prev?.gdriveId, mimeType = mimeForDoc(f.docType),
            fileSizeBytes = f.fileSizeBytes.toLongOrNull(), pageCount = f.pageCount.toIntOrNull(),
            extractedText = f.extractedText.takeIf { it.isNotBlank() },
            extractionMethod = prev?.extractionMethod ?: "manual"))
        return id
    }

    private suspend fun saveMedia(f: EntryFormState): String {
        if (isNew) return repo.createMedia(f.title.trim(), f.content.takeIf { it.isNotBlank() },
            f.mediaType, "", mimeForMedia(f.mediaType),
            ocrText = f.ocrText.takeIf { it.isNotBlank() },
            caption = f.caption.takeIf { it.isNotBlank() })
        val id = entryId!!; val prev = repo.getMedia(id)
        repo.updateEntryCommon(id, f.title.trim(), f.content.takeIf { it.isNotBlank() })
        repo.upsertExtension(EntryMediaEntity(
            entryId = id, mediaType = f.mediaType, blobPath = prev?.blobPath ?: "",
            mimeType = mimeForMedia(f.mediaType),
            widthPx = prev?.widthPx, heightPx = prev?.heightPx, durationS = prev?.durationS,
            ocrText = f.ocrText.takeIf { it.isNotBlank() },
            caption = f.caption.takeIf { it.isNotBlank() }))
        return id
    }

    private suspend fun savePerson(f: EntryFormState): String {
        val name = f.fullName.ifBlank { f.title }.trim()
        if (isNew) return repo.createPerson(f.title.trim(), f.content.takeIf { it.isNotBlank() },
            name, splitList(f.aliasesText), f.birthYear.toIntOrNull(), f.deathYear.toIntOrNull(),
            f.nationality.takeIf { it.isNotBlank() }, splitList(f.occupationsText),
            f.biography.takeIf { it.isNotBlank() })
        val id = entryId!!; val prev = repo.getPerson(id)
        repo.updateEntryCommon(id, f.title.trim(), f.content.takeIf { it.isNotBlank() })
        repo.upsertExtension(EntryPersonEntity(
            entryId = id, fullName = name,
            aliasesJson = toJsonArray(splitList(f.aliasesText)),
            birthYear = f.birthYear.toIntOrNull(), deathYear = f.deathYear.toIntOrNull(),
            nationality = f.nationality.takeIf { it.isNotBlank() },
            occupationsJson = toJsonArray(splitList(f.occupationsText)),
            biography = f.biography.takeIf { it.isNotBlank() }, photoPath = prev?.photoPath))
        return id
    }

    private suspend fun saveOrg(f: EntryFormState): String {
        val name = f.officialName.ifBlank { f.title }.trim()
        if (isNew) return repo.createOrg(f.title.trim(), f.content.takeIf { it.isNotBlank() },
            name, f.orgType.takeIf { it.isNotBlank() }, f.foundedYear.toIntOrNull(),
            f.country.takeIf { it.isNotBlank() }, f.websiteUrl.takeIf { it.isNotBlank() },
            f.description.takeIf { it.isNotBlank() })
        val id = entryId!!
        repo.updateEntryCommon(id, f.title.trim(), f.content.takeIf { it.isNotBlank() })
        repo.upsertExtension(EntryOrgEntity(
            entryId = id, officialName = name, orgType = f.orgType.takeIf { it.isNotBlank() },
            foundedYear = f.foundedYear.toIntOrNull(), country = f.country.takeIf { it.isNotBlank() },
            websiteUrl = f.websiteUrl.takeIf { it.isNotBlank() },
            description = f.description.takeIf { it.isNotBlank() }))
        return id
    }

    private suspend fun savePlace(f: EntryFormState): String {
        val name = f.placeName.ifBlank { f.title }.trim()
        if (isNew) return repo.createPlace(f.title.trim(), f.content.takeIf { it.isNotBlank() },
            name, f.placeType.takeIf { it.isNotBlank() }, f.address.takeIf { it.isNotBlank() },
            f.latitude.toDoubleOrNull(), f.longitude.toDoubleOrNull())
        val id = entryId!!; val prev = repo.getPlace(id)
        repo.updateEntryCommon(id, f.title.trim(), f.content.takeIf { it.isNotBlank() })
        repo.upsertExtension(EntryPlaceEntity(
            entryId = id, placeName = name, placeType = f.placeType.takeIf { it.isNotBlank() },
            address = f.address.takeIf { it.isNotBlank() },
            latitude = f.latitude.toDoubleOrNull(), longitude = f.longitude.toDoubleOrNull(),
            visitedDatesJson = prev?.visitedDatesJson ?: "[]"))
        return id
    }

    private suspend fun saveEvent(f: EntryFormState): String {
        val name = f.eventName.ifBlank { f.title }.trim()
        val start = f.startedAt ?: System.currentTimeMillis()
        if (isNew) return repo.createEvent(f.title.trim(), f.content.takeIf { it.isNotBlank() },
            name, start, f.endedAt, f.locationText.takeIf { it.isNotBlank() },
            participants = splitList(f.participantsText))
        val id = entryId!!; val prev = repo.getEvent(id)
        repo.updateEntryCommon(id, f.title.trim(), f.content.takeIf { it.isNotBlank() })
        repo.upsertExtension(EntryEventEntity(
            entryId = id, eventName = name, startedAt = start, endedAt = f.endedAt,
            locationText = f.locationText.takeIf { it.isNotBlank() },
            placeEntryId = prev?.placeEntryId, isPersonal = prev?.isPersonal ?: true,
            participantsJson = toJsonArray(splitList(f.participantsText))))
        return id
    }

    private suspend fun saveLiked(f: EntryFormState): String {
        if (isNew) return repo.createLiked(f.title.trim(), f.content.takeIf { it.isNotBlank() },
            f.likedPlatform.ifBlank { "other" },
            f.originalId.ifBlank { UUID.randomUUID().toString() },
            f.likedContentType.ifBlank { "post" },
            f.likedAuthorName.takeIf { it.isNotBlank() },
            f.likedFullText.takeIf { it.isNotBlank() })
        val id = entryId!!; val prev = repo.getLiked(id)
        repo.updateEntryCommon(id, f.title.trim(), f.content.takeIf { it.isNotBlank() })
        repo.upsertExtension(EntryLikedEntity(
            entryId = id, platform = f.likedPlatform.ifBlank { "other" },
            originalId = f.originalId.ifBlank { prev?.originalId ?: UUID.randomUUID().toString() },
            likedAt = prev?.likedAt ?: System.currentTimeMillis(),
            contentType = f.likedContentType.ifBlank { "post" },
            authorName = f.likedAuthorName.takeIf { it.isNotBlank() },
            fullText = f.likedFullText.takeIf { it.isNotBlank() }))
        return id
    }

    private suspend fun saveAiConv(f: EntryFormState): String {
        if (isNew) return repo.createAiConv(f.title.trim(), f.content.takeIf { it.isNotBlank() },
            f.aiModel.ifBlank { "unknown" }, f.aiProvider,
            topic = f.aiTopic.takeIf { it.isNotBlank() },
            messagesJson = toMessagesJson(f.aiMessagesText))
        val id = entryId!!; val prev = repo.getAiConv(id)
        repo.updateEntryCommon(id, f.title.trim(), f.content.takeIf { it.isNotBlank() })
        repo.upsertExtension(EntryAiConvEntity(
            entryId = id, model = f.aiModel.ifBlank { "unknown" }, provider = f.aiProvider,
            messagesJson = toMessagesJson(f.aiMessagesText).takeIf { it != "[]" }
                ?: prev?.messagesJson ?: "[]",
            tokenCount = prev?.tokenCount, topic = f.aiTopic.takeIf { it.isNotBlank() },
            isUseful = prev?.isUseful))
        return id
    }

    // ── ヘルパー ──────────────────────────────────────────────

    private fun splitList(text: String): List<String> =
        text.split(",", "、", "\n").map { it.trim() }.filter { it.isNotEmpty() }

    private fun toJsonArray(list: List<String>): String =
        JsonArray(list.map { JsonPrimitive(it) }).toString()

    private fun parseList(json: String?): List<String> = try {
        Json.parseToJsonElement(json ?: "[]").jsonArray.map { it.jsonPrimitive.content }
    } catch (_: Exception) { emptyList() }

    private fun extractMessagesText(messagesJson: String): String = try {
        Json.parseToJsonElement(messagesJson).jsonArray
            .mapNotNull { it.jsonObject["content"]?.jsonPrimitive?.content }
            .joinToString("\n\n")
    } catch (_: Exception) { "" }

    private fun toMessagesJson(text: String): String {
        if (text.isBlank()) return "[]"
        val now = System.currentTimeMillis()
        return JsonArray(text.split("\n\n").filter { it.isNotBlank() }.map { part ->
            buildJsonObject {
                put("role", "user"); put("content", part.trim()); put("timestamp", now)
            }
        }).toString()
    }

    private fun extractDomain(url: String): String =
        try { java.net.URI(url.trim()).host ?: url.trim() } catch (_: Exception) { url.trim() }

    private fun mimeForDoc(t: String) = when (t) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "md" -> "text/markdown"; "txt" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun mimeForMedia(t: String) = when (t) {
        "image" -> "image/*"; "audio" -> "audio/*"; "video_file" -> "video/*"
        else -> "application/octet-stream"
    }
}