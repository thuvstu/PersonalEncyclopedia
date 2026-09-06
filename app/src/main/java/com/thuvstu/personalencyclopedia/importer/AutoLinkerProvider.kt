package com.thuvstu.personalencyclopedia.importer

import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AutoLinker(Trie)をアプリ全体で共有キャッシュする。
 * ★wt44: 「件数-最終更新時刻」の指紋(`EntryDao.linkerFingerprint`、COUNT+MAX の1クエリ)が
 * 変わったときだけ再構築する。エントリーの追加・改名・削除・取込が自動で反映され、
 * 変化が無ければ詳細画面を何度開いてもTrieを作り直さない(旧: 画面ごとに5000件読込→構築)。
 * 構築はタイトル射影(`getAllTitles`)のみ読むので本文の大きさに影響されない。
 */
@Singleton
class AutoLinkerProvider @Inject constructor(
    private val entryDao: EntryDao
) {
    private val mutex = Mutex()
    @Volatile private var cached: AutoLinker? = null
    @Volatile private var cachedFingerprint: String? = null

    suspend fun get(): AutoLinker {
        val fp = try { entryDao.linkerFingerprint() } catch (_: Exception) { null }
        cached?.let { if (fp != null && fp == cachedFingerprint) return it }
        return mutex.withLock {
            cached?.let { if (fp != null && fp == cachedFingerprint) return@withLock it }
            val titles = entryDao.getAllTitles().map { it.id to it.title }
            AutoLinker.fromTitles(titles).also { cached = it; cachedFingerprint = fp }
        }
    }

    fun invalidate() { cached = null; cachedFingerprint = null }
}
