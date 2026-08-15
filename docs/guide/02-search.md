# 02-search.md — Hybrid Search(FTS4 + Embedding + RRF)が実際に何をしているか

検索は `brain/search/HybridSearchEngine.kt` の `search()` が、`LIKE / FULLTEXT / SEMANTIC / HYBRID` の4モードを切り替えて実行する。既定は HYBRID。

## 1. 全文検索(FTS4 + バイグラム)

日本語は英語と違って単語の区切りが明確でないため、SQLite の形態素解析器を素直に使えない。
そこで `NgramTokenizer`(設計書 §7.2.1)が、**重なり合う2文字ずつ**(バイグラム)に分割して FTS4 に流し込む。

例: `量子力学` → `量子 子力 力学` のテキストを `search_document` の FTS テーブルに入れる。
検索クエリ側も同じ分割をして `"量子" OR "子力" OR "力学"` で MATCH する(部分一致になる)。

- 検索専用テキスト `search_document.combined_text` は、entry 本体＋型ごとの拡張情報＋定義 を `EmbeddingTextBuilder` が1本に結合したもの。
- entry が変わると `EmbeddingQueue.updateSearchDocument()` が FTS 行を作り直す。

## 2. ベクトル検索(Embedding + コサイン類似度)

`GeminiClient.embed()` で検索クエリも768次元ベクトルにし、`InMemoryVectorIndex.topK()` で全 entry とのコサイン類似度を**総当たり**で計算して上位 k 件を返す(§7.1.5)。

- 全ベクトルは起動時にメモリへ載せる(数千〜数万件でも数十msの規模。§14 想定データ規模参照)。
- `InMemoryVectorIndex` は**イミュータブルなスナップショット + AtomicReference** で並行アクセス安全にしてある(§7.6、テストは `InMemoryVectorIndexConcurrencyTest` 参照)。
- 埋め込みが未設定(Gemini API キー未設定)のときは、この経路は空になる(全文検索だけが動く)。

## 3. RRF 合算(Reciprocal Rank Fusion)

両検索の「順位」だけを使って1本に混ぜる。

```
score(id) = 1/(60+fulltext順位) + 1/(60+semantic順位)
```

- 60 は `RRF_K` という定数。大きいほど順位の差を緩く扱う(どちらか一方にヒットしていれば加点される)。
- その後、作成から7日以内は +0.05、30日以内は +0.02 の**新しさボーナス**を足す。
- ミュート中・削除済みの entry は結果から除く。

## なぜこの構成か

- FTS4 だけだと「意味が近いが文字列が違う」ものが引けない。
- Embedding だけだと「固有名詞・部分文字列」のヒットが弱い(768次元ベクトルは文意の近似であって文字列照合ではない)。
- 両者の**順位**を混ぜる RRF は、スコアの単位や分布が違う問題を回避できる(設計書 §7.2.2)。

## どこで使われているか

- アプリ内: `HybridSearchEngine`(検索画面)、`SemanticGrader`(クイズ採点の下支え)、`ResurfacingEngine`(リサーフェシング)
- サーバー: `GET /api/search`(Web クライアント用、§10)

- 参考: 設計書 §7.1(埋め込み)、§7.2(検索)、§7.6(スレッドセーフ)
