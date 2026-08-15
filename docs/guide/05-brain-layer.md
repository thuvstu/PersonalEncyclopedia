# 05-brain-layer.md — Embeddingキュー・スレッドセーフ設計・LLMモデル選択の仕組み

`brain/` は「検索の下支え・AI まわり」を担う層。embedding の生成はスループットと信頼性に効くので、キュー+ジョブ管理で設計している(§7.1)。

## 1. Embedding キュー(`brain/ai/EmbeddingQueue.kt`)

entry を保存すると `enqueue(entryId)` が呼ばれ、処理は `Dispatchers.IO` の単一ワーカーが Channel から逐次取り出す。

```
enqueue(entryId)
 ├─ updateSearchDocument(): 検索用結合テキストを作成し FTS に登録(ここまで無料、API不要)
 └─ Gemini が未設定なら終了
     既存ジョブが done で入力テキストが同じならスキップ(重複埋め込み防止)
     embedding_job に queued を書き、Channel へ送る
```

ワーカー側(`processEmbedding`)で埋め込みを取得し、`embedding` テーブルとインメモリ索引の両方に反映する。

- **再試行**: 失敗すると attempts を増やして再キュー。最大 `MAX_ATTEMPTS=3`。
- **復旧**: `recoverJobs()` が起動時に `queued/running` の未完了ジョブを再投入する(アプリが途中で死んだ場合の保険)。
- **全文再構築**: `rebuildAllSearchDocuments()` が全 entry の FTS を再作成。

## 2. スレッドセーフ設計(§7.6、D1 GAP-4)

`InMemoryVectorIndex` は検索スレッドと埋め込みワーカーが同時に触るため、**イミュータブルなスナップショット + AtomicReference + CAS ループ**で守っている。

- 読み(`topK`)はスナップショットを1回だけ取得して使う → 読んでいる途中に書き込みが割り込んでも、常に一貫したデータを見る。
- 書き(`addVector` / `removeVector`)は compare-and-swap で、競合したらやり直すループ。

> なぜロック(排他)でなく CAS か: 読み取りが過半数で、書き込みは稀(埋め込み完了時)だから。ロックより実装が単純で、読み取りを一切ブロックしない。

並行アクセスの検証は `InMemoryVectorIndexConcurrencyTest`(同時 topK/addVector/removeVector でクラッシュしない・件数が不整合にならない)。

## 3. LLM モデル選択(`brain/ai/GeminiClient.kt`, `OllamaClient.kt`, `AiModels.kt`)

- Gemini は `AiModels.GEMINI_CHAT_MODELS` の一覧から `geminiModel` を選ぶ。モデル名は設定画面(`SettingsScreen`)で変更でき、コードにハードコードしない(§7.4.4)。
- 未設定(`isConfigured()==false`)のときは関連機能が安全に OFF になる(検索は全文のみ、埋め込みは生成しない)。
- Ollama は LAN 内サーバーへ OpenAI 互換エンドポイント(`/v1/chat/completions`)で接続(§7.7)。埋め込みは Gemini と次元数・意味空間が違うため、混在時は `embedding.model` カラムで区別し、**同一モデルのベクトル同士だけ**で類似度を計算する(§7.1.1)。
- PC の Web クライアントからも直接 Ollama を呼べる(`web/src/lib/ollamaClient.ts`、§7.7)。データの読み書きは Ktor API 経由のままなので「Android=データ本体」は崩れない。

- 参考: 設計書 §7(思考層)、§7.1(埋め込み)、§7.6(スレッドセーフ)、§7.7(Ollama)
