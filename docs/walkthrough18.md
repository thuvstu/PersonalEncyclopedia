# walkthrough18 — largeHeap撤去で通常ヒープに戻す

**日付:** 2026-09-04
**コミット:** bee0efc
**ビルド:** assembleDebug BUILD SUCCESSFUL (app-debug.apk 再生成確認)

## 1. 背景

* walkthrough13 で 50k 投入時の `InMemoryVectorIndex.load()` OOM を `count()>10k` での全件ロード skip により解消 (load 9.8s→4ms, Pss 553M→208M)。
* `android:largeHeap="true"` はその前の暫定対応として追加されたもの。skip後は 50k でも Heap 77M (BASELINE.md §4) で通常ヒープ上限 256MB に十分収まるため、残タスク「largeHeap 要否の再評価」(walkthrough14 §5) の結論として撤去する。
* largeHeap は GC 一時停止の長期化・他アプリへのメモリ圧迫・Play ストア警告の原因になるため、外して通常ヒープに戻す方が「軽い」。

## 2. 変更 (1ファイル1行)

* 対象: `app/src/main/AndroidManifest.xml:17`
* 変更: `android:largeHeap="true"` の1行削除のみ。
* 安全性の根拠: `brain/search/InMemoryVectorIndex.kt:44-55` で `embeddingDao.count() > 10_000` 時は `Snapshot.EMPTY` のまま sqlite-vec (`EmbeddingDao.vecSearch` の `vec_distance_cosine`) に委譲し、147MB×2 のヒープ確保自体が発生しない。10k 以下は従来通り最大 30MB 程度で安全。

## 3. 検証

* `./gradlew assembleDebug` 成功。`app/build/outputs/apk/debug/app-debug.apk` の再生成と `git diff` が1行削除のみであることを確認。
* 実機での 50k seed 起動確認は次回実機セッションで実施し、結果を `docs/perf/BASELINE.md` §4-5 に追補する (クラッシュせず TotalTime 800ms 前後・load 4ms 前後であること)。

## 4. 次の一手 (1機能ずつ、別セッション)

* 実機 50k seed での largeHeap 無し起動確認 → BASELINE 追補
* FTS 膨張 (DB 624M) 対策の検討 (`search_document_fts` の VACUUM/FTS5)
* `vec_distance_cosine` の本番運用確認 (Gemini 設定時の semantic 検索が非空)
