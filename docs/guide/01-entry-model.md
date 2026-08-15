# 01-entry-model.md — entry 統一型 + CTI(Class Table Inheritance)

## なぜ「統一型」か

タグ・トピック・単語帳・クイズを全部別物として持つと、検索や関連付けが一覧で効かなくなる。
「これは知識のかたまり」と1つの `entry` テーブルに統一し、**型の違いは別テーブルに寄せる**ことで、
検索・接続・復習を型を問わず共通で扱えるようにしている(設計書 §5.1)。

## テーブル構成

```
entry            ← 共通カラムを持つ本体(タイトル・本文・サマリー・お気に入り…)
entry_extension  ← 型ごとの追加情報(例: book なら著者・出版社)
entry_definition ← 単語帳用(term/definition/reading/field)
entry_tag        ← タグ
connection       ← entry同士の関連(承認済み)
connection_candidate ← 自動生成された関連候補(承認待ち)
```

- `type` カラム(文字列)がどの型かを表す。13型は次のとおり。
  `thought / definition / webpage / book / video / document / media / person / org / place / event / liked / ai_conv`

## 13型の一覧

| type | 意味 | 主な追加情報 |
|---|---|---|
| thought | 思考・感想 | — |
| definition | 単語・用語 | term, definition, reading, field(単語帳トラックの本体) |
| webpage | Webページ | url, title, author など |
| book | 本 | 著者, 出版社, 刊行年 など |
| video | 動画 | チャンネル, 長さ など |
| document | ドキュメント | ファイル種別, ソース など |
| media | メディア | 種別, メタデータ など |
| person | 人物 | 生没年, 職業 など |
| org | 組織 | 種別, 所在地 など |
| place | 場所 | 座標, 地域 など |
| event | 出来事 | 開始日, 終了日 など |
| liked | いいね収集 | 元URL など |
| ai_conv | AI対話 | プロバイダ, モデル など |

型の表示ラベル・色は `entry_type` テーブル(EntryTypeEntity)にあり、`SeedData.kt` が初期投入する。
Web クライアント側は `web/src/lib/entryTypes.ts` に同じ一覧を持つ(API に型一覧エンドポイントが無いため)。

## なぜ text でなく文字列カラムなのか

型を enum ではなく String で持つのは、**新しい型を DB マイグレーションなしで追加できる**ようにするため。
`entry_type` にシードを足せば UI のラベル・色も揃う。将来カスタムフィールド(§5.8.3)を足す土台もこの思想。

## 関連設計

- 削除は物理削除でなく `deletedAt` による**論理削除**(`EntryDao.softDelete`)。
- マイグレーションは `db/Migration1to2.kt` 〜 `Migration7to8.kt` のようにバージョンごとにファイルを分ける。
  テストは `MigrationTestHelper` での検証を原則とする(§14 非機能要件)。

- 参考: 設計書 §5.1(統一型+CTI)、§5.8(拡張テーブル)
