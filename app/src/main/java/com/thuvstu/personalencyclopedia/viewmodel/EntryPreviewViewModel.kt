package com.thuvstu.personalencyclopedia.viewmodel

import androidx.lifecycle.ViewModel
import com.thuvstu.personalencyclopedia.db.entity.EntryDefinitionEntity
import com.thuvstu.personalencyclopedia.db.entity.EntryEntity
import com.thuvstu.personalencyclopedia.repository.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * §12.5 定義プレビュー用の軽量ViewModel。
 * EntryPreviewPopupが任意のentryIdを対象に観測できるよう、entryIdは引数で受け取る。
 */
@HiltViewModel
class EntryPreviewViewModel @Inject constructor(
    private val repo: EntryRepository
) : ViewModel() {

    fun observeEntry(entryId: String): Flow<EntryEntity?> = repo.observeEntry(entryId)

    fun observeDefinition(entryId: String): Flow<EntryDefinitionEntity?> =
        repo.observeDefinition(entryId)
}
