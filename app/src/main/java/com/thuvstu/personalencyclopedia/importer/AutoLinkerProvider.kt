package com.thuvstu.personalencyclopedia.importer

import com.thuvstu.personalencyclopedia.db.dao.EntryDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AutoLinker(Trie)をアプリ全体で共有キャッシュする。
 * ★wt44: 「件数-最終更新時刻」の指紋(`EntryDao.linkerFingerprint`、COUNT+MAX の1クエリ)が
 * 変わったときだけ再構築する。エントリーの追加・改名・削除・取込が自動で反映され、
 * 変化が無ければ詳細画面を何度開いてもTrieを作り直さない(旧: 画面ごとに5000件読込→構築)。
 * 構築はタイトル射影(`getAllTitles`)のみ読むので本文の大きさに影響されない。
 * ★wt46: 読み・別名などの表層形(`getAllAliasForms`)も同じTrieに登録し、表記揺れを同じentryに解決する。
 * 拡張テーブルの編集も `entry.updatedAt` を更新する(`updateDefinition`/`updateEntryCommon`)ため指紋で検知できる。
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
            // 別名は取得失敗してもタイトルのみで動作を継続する
            val aliasRows = try { entryDao.getAllAliasForms() } catch (_: Exception) { emptyList() }
            val aliases = expandAliasForms(aliasRows.map { it.id to it.title })
            AutoLinker.fromTitles(titles, aliases).also { cached = it; cachedFingerprint = fp }
        }
    }

    fun invalidate() { cached = null; cachedFingerprint = null }

    companion object {
        /**
         * `getAllAliasForms` の行を (id, 表層形) に展開する。
         * `["a","b"]` 形式のJSON配列(人物 aliasesJson)は要素ごとに分解し、それ以外は1表層形として扱う。
         * `[` で始まる値はJSON配列扱い(解釈不能なら捨てる)。
         * 純粋関数(JVMテスト可能)。
         */
        fun expandAliasForms(rows: List<Pair<String, String>>): List<Pair<String, String>> {
            val out = ArrayList<Pair<String, String>>(rows.size)
            for ((id, raw) in rows) {
                val v = raw.trim()
                if (v.isEmpty()) continue
                if (v.startsWith("[")) {
                    // JSON配列として解釈できなければ捨てる(壊れたJSONを表層形として登録しない)
                    val items = try {
                        Json.parseToJsonElement(v).jsonArray.map { it.jsonPrimitive.content }
                    } catch (_: Exception) { emptyList() }
                    for (item in items) if (item.isNotBlank()) out.add(id to item.trim())
                } else {
                    out.add(id to v)
                }
            }
            return out
        }
    }
}
