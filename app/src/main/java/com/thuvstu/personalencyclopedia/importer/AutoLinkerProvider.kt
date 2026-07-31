package com.thuvstu.personalencyclopedia.importer

import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AutoLinker(Trie)をアプリ全体で1回だけ構築してキャッシュする。
 * 画面を開くたびに再構築しない（高速化）。
 * インポート等でentryが増えた場合は invalidate() を呼ぶ。
 */
@Singleton
class AutoLinkerProvider @Inject constructor(
    private val entryDao: EntryDao
) {
    private val mutex = Mutex()
    @Volatile private var cached: AutoLinker? = null

    suspend fun get(): AutoLinker {
        cached?.let { return it }
        return mutex.withLock {
            cached?.let { return@withLock it }
            val entries = entryDao.observeAll(limit = 50000).first()
            AutoLinker(entries).also { cached = it }
        }
    }

    fun invalidate() { cached = null }
}