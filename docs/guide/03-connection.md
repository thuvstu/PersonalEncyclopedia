# 03-connection.md — 接続候補承認フローと「自動接続が既定でOFF」の理由

## entry 同士の「つながり」はどう作られるか

つながり(connection)を作る経路は2つ。

1. **手動**: 画面から entry A と B を選び、関係種別(`related / references / contradicts / extends / exemplifies / authored_by / …`)を指定して作る。`ConnectionEngine.createManualConnection()`。
2. **自動候補**: 埋め込みベクトルが近い entry 同士を、相似度が閾値(既定 0.88)以上のとき候補として `connection_candidate` テーブルに入れる。ユーザーが**承認**して初めて正式な connection になる。

## 候補生成の流れ(`ConnectionEngine.generateCandidatesForEntry`)

```
1. autoConnectEnabled でなければ何もしない
2. ベクトル索引から似ている上位10件を取り出す
3. 相似度 < 閾値 はスキップ、自分自身はスキップ
4. 既に候補が存在するペアはスキップ
5. connection_candidate に「related」として insert
```

- 型定義は `seedTypeDefs()` が `connection_type_def` に初期投入する(related は無向、references は有向など)。
- 有向型は A→B と B→A を別物として重複チェックし、無向型は A/B の辞書順(`canonicalA/B`)に正規化して重複を避ける。

## 承認(`approveCandidate`)

候補を承認すると `createManualConnection()` を呼び、相似度を strength として connection を作成し、
候補の status を `approved` に更新する。拒否は `rejected`。

## なぜ「自動接続」を既定で OFF にしているか(設計書 §8.4)

- 自動で大量につながりを作ると、**誤った関連を量産**してナレッジグラフがノイズまみれになる。
- 検索と違い、関連は「本人の意図」を反映したい。自動候補はあくまで**提案**であり、人間の承認を挟む。
- `AUTO_CONNECT_ENABLED` を ON にするのは、設計書 §5.5.3 の「3条件」(検索・埋め込みの品質が安定し、承認率が高いと確認できた等)を満たした後に判断する。

## グラフ表示

`connection` を元に `ConnectionDao.traverseGraph(entryId, depth)` が BFS 的に関連を辿って返す。
PC 側では `/api/graph` を React Flow で描画する(web/src/components/GraphView.tsx)。Android 単体では画面の制約から関連リスト+バックナビゲーションで簡略表示する(§11.6)。

- 参考: 設計書 §8.4(接続候補)、§5.5(スキーマ)、§7.1.5(ベクトル索引)
