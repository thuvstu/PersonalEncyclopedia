# walkthrough37 — rerank第二段階の実装（LLM judge方式）

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `compileDebugKotlin` BUILD SUCCESSFUL
**根拠:** mismatch §3.3、RealityTasks「rerankingモデル」

## 1. 背景

意味検索に第二段階の精密化が無かった。`ICrossEncoderProvider` は定義のみ、
呼出0件だった。端内モデル（Qwen3-Reranker等）までの繋ぎとしてLLM judge方式を実装する。

## 2. 変更 (2ファイル)

* 新規 `brain/search/SemanticReranker.kt` (約80行):
  - 上位10件のタイトル+要約をLLMに投げ、関連度0-100を取得。
  - RRF正規化スコアと5:5ブレンドで並べ替え。
  - API未設定・失敗時は元の順序を返す（graceful degradation）。
  - 将来の端内モデル差し替え点（このクラスだけ置換）。
* `brain/search/HybridSearchEngine.kt` (+約10行):
  FULLTEXT・SEMANTIC・HYBRIDの3経路に組込（候補をlimit*2集めてrerank→limit）。

## 3. 検証

* コンパイル成功。API未設定時は従来通りの順序（劣化なし）。
  実機では設定時に上位の入れ替わりと遅延を確認する。

## 4. 次の一手

* SAFフォルダ取込・bottomBar state・件数表記（#U1）。
