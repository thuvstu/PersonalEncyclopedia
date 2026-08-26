# walkthrough10 — M-3軽量計測ベースライン実測(1k/10k/50k)

**日付:** 2026-08-26 23:20〜23:42 JST
**対象:** NextTasks.md M-3(改訂)「50,000件データでの軽量計測実測値を軽量計測で記録する」
**端末:** 25053PC47G (Xiaomi, SM8735/QTI, arm64-v8a, Android 16, RAM 11.5GB+Swap12GB)
**ビルド:** debug (`:app:installDebug`, commit 94a6692), USB給電, バッテリー30%前後

---

## 1. 事前準備

- `PerfSeedReceiver` に `SEARCH` アクションを追加(`SEARCH --es query bench` で `歴史/細胞分裂/量子もつれ` の3クエリを `timed()` 経由で計測)。debug専用のためrelease APKに影響なし
- `util/Timed.kt` は前回セッション( walkthrough9 )で導入済み。`InMemoryVectorIndex.load` と `hybridSearch` に仕込み済み
- 計測前に `adb shell pm clear` でDBを初期化。`SyntheticDataSeeder` は `seed(count)` 毎に既存合成データを全削除→投入するため各規模は独立

## 2. 投入とDBサイズ

| count | seed時間 | DB (`du -h` / `ls -lh encyclopedia.db`) | 備考 |
|---|---|---|---|
| 1,000 | 0.9s | 16M (15M+32K+512K) | 初回 14M, 再測 16M (WAL差) |
| 10,000 | 7.6s | 131M (131M+64K+512K) |  |
| 50,000 | 38.3s | 625M (625M+256K+30M) ※656M total | WAL含むと656M |

線形スケール: 1k 16M → 10k 131M(8倍) → 50k 625M(5倍)。768dim×4B×50k=147MBのベクトルが支配的。

## 3. コールドスタート (`am start -W`, 5回中央値)

`adb shell am force-stop` → `am start -W -n .../.MainActivity` を5回。

| 規模 | TotalTime(中央値) | 5回生値 | InMemoryVectorIndex.load(中央値) |
|---|---|---|---|
| 0 | — | — | 2ms |
| 1,000 | **798ms** | 771,792,798,804,841 | **443ms** (400,412,443,462,463) |
| 10,000 | **767ms** | 760,765,767,769,774 | **911ms** (873,874,911,913,940) |
| 50,000 | 780ms※ | 771,779,780,865,897 | **OOM** |

※50kは `TotalTime` 自体は出力されるが直後にクラッシュするため参考値。

`TotalTime` 自体は規模にほぼ依存しない(700ms台で横ばい)。`load` が規模に比例して増大。

## 4. 検索応答 (`PerfSeedReceiver.SEARCH --es query bench`)

FTS+RRF経路のみ( Gemini未設定のためsemanticスキップ)。3クエリ連続実行。

| 規模 | 歴史 | 細胞分裂 | 量子もつれ | 備考 |
|---|---|---|---|---|
| 1,000 | 73ms (初回86) | 36ms | 37ms | 1回目はキャッシュミスでやや遅い |
| 10,000 | 79ms | 77ms | 74ms | 件数増でも70ms台で安定(FTSはlogN) |
| 50,000 | OOM | OOM | OOM | アプリ自体が起動しないため計測不可 |

FTS+Nグラムは10kでも十分高速。ボトルネックはセマンティック側ではなく全件ロード側。

## 5. スクロール (`dumpsys gfxinfo`)

自動 `input swipe` ではフレーム数が10〜16と少なすぎてjank率が不安定(43〜50%)。
リセット前の自然操作では 205フレームで5.85% jank (1k時)。手動フリングでの再計測が必要。
50kはクラッシュのため計測不可。

## 6. RAM (`dumpsys meminfo` TOTAL Pss)

| 規模 | Pss | Heap Size/Alloc | 備考 |
|---|---|---|---|
| 0 | 201M | 68M/35M |  |
| 1,000 | 216M | 81M/37M |  |
| 10,000 | 249M | 178M/66M |  |
| 50,000 | OOM | — | heap limit 256M超過 |

## 7. 50k OOMクラッシュ詳細(PERF-8破綻)

**ログ:**
```
Failed to allocate a 3088 byte allocation with 588624 free bytes and 574KB until OOM,
target footprint 268435456, growth limit 268435456
  at GeminiClientKt.toFloatArray(GeminiClient.kt:131)
  at InMemoryVectorIndex.load(InMemoryVectorIndex.kt:44)
```

**原因:** `embeddingDao.getAll()` でByteArray(3KB×50k=147MB)を一括ロード → `toFloatArray()` でFloatArray(同147MB)を生成。
一時的に両方がヒープに共存し 300MB近くを占有、256M上限を超過。

**結論:** `DESIGN.md §7.1.5` の「数万件までブルートフォースで実用」は **10kまでは成立(900ms/70msで実用)、50kで破綻**。
NextTasks.md Round 5のsqlite-vec移行が最優先課題として確定。

**再現:** `am broadcast ... --ei count 50000` → `am force-stop; am start -W` → 10秒後にクラッシュ。
**復旧:** `adb shell run-as ... rm databases/encyclopedia.db*` または `pm clear`。計測後は `...perf.CLEAR` で合成データを削除済み(23M残存、VACUUM未実施)。

## 8. 変更ファイル

- `app/src/debug/.../PerfSeedReceiver.kt` — SEARCHアクション追加(hybridSearchベンチ用)
- `docs/perf/BASELINE.md` — §4記録表を実測値で全面更新、§5にOOM詳細追記
- `docs/NextTasks.md` — M-3/PERF-8を完了扱いに更新、Round5をOOM対応へ書き換え

## 9. ビルド確認

- `:app:assembleDebug` 成功
- 実機で 1k/10k/50k の3段階で `SyntheticSeeder` ログ・`App` タグ・`du -h`・`dumpsys` を確認
- 50kクラッシュはlogcatで再現・復旧を確認

## 10. 次の一手

1. **Round 5最優先:** `largeHeap=true` の暫定緩和 vs `sqlite-vec` 本命移行の検討。`InMemoryVectorIndex` を起動時全件ロードから廃止
2. **Round 1:** PERF-1(WAL明示化) / PERF-2(progress_events索引) — DB層の低リスク最適化
3. **再計測:** 各Round後に本手順( §4 )で50k再計測しBASELINE差分を記録

