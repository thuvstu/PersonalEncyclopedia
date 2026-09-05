# walkthrough36 — バックアップのストリーミング化＋FTS差分更新 (#K1)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `compileDebugKotlin` BUILD SUCCESSFUL
**根拠:** DESIGN §13 B4・E3・§15 #K1/#F2、mismatch §6-1

## 1. 背景

`BackupEncryptor` の `readBytes()` 全載せはGB級でOOM確実。
起動時 `rebuildAllSearchDocuments` は全件FTS再構築で逆スケール（5万件常用の最大障害）。

## 2. 変更 (2ファイル)

* `backup/BackupEncryptor.kt` (+約20行):
  暗号化・復号を8KBチャンクのストリーミング化（CipherOutputStream/InputStream）。
  出力形式 `[12B IV][cipher+tag]` は同一のため既存 `.enc` と互換あり。
  復号タグ検証はストリーム終端で実施（破損時はIOException）。
* `brain/ai/EmbeddingQueue.kt` (+約25行):
  - `updateSearchDocument` に内容不変スキップ（enqueue経路の冪等化）。
  - `rebuildAllSearchDocuments` を差分化（`entry.updatedAt <= doc.updatedAt` は読飛ばし、
    削除・文書なし・空は掃除のため処理）。更新/スキップ件数をログ出力。

## 3. 検証

* コンパイル成功。実機では (a) 新旧 `.enc` の復元互換、(b) 2回目以降の起動が
  `skipped` 主体になることをlogcatで確認する。
* FTS膨張自体（624M）の対策（VACUUM/FTS5）は別途。

## 4. 次の一手

* rerank（LLM judge方式）の実装。
