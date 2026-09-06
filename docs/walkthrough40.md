# walkthrough40 — 白板エッジ（接続線）の表示・作成・編集 (P3-1〜P3-3)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** サンドボックスにAndroid SDK/Gradle配布が到達不能（dl.google.com / services.gradle.org 遮断）のため
`compileDebugKotlin` は未実行。kotlinc 2.4.10 単体での構文検査のみ実施（Compose/Room未解決以外のエラー0件）。
**⚠ 次回の実機セッションで最初に `./gradlew :app:compileDebugKotlin` を必ず通すこと。**
**根拠:** 強化計画 Phase 3（P3-1/P3-2/P3-3）、mismatch §1.1「接続線(エッジ)表示なし」、GachiPKM `whiteboard_edges` スキーマ参考

## 1. 背景

Heptabaseライク画面の核心「関係可視化」が欠けていた。キャンバスはカードの `forEach` のみで、
エッジ用のEntity/DAO/描画が一切無かった（mismatch §1.1）。PC側 `GraphView` は connection の
可視化であり、Android白板と非連動。

## 2. 設計判断（P3-2: connection との対応付け）

**白板固有のエッジ**とした。理由:
- `connection` は承認制（`connection_candidate` → 承認）で「知識グラフの事実」を表す。
  白板の線は「この思考空間での並べ方・関係の仮置き」であり、性格が違う。
- 線を張るだけで connection に自動書込すると AGENTS.md の骨格（承認フロー必須）を崩す。
- 白板ノードは entry 参照だけでなく型なし note も持つ。connection は entry 間にしか張れない。

将来「この線を connection に昇格」ボタンを付ける余地は残す（エッジ→候補作成→承認）。

## 3. 変更 (9ファイル)

* `db/entity/WhiteboardEntities.kt` (+20行): `WhiteboardEdgeEntity`
  (`id/boardId/sourceNodeId/targetNodeId/label/colorHex/createdAt`、索引3本)。向きは持たない。
* `db/dao/WhiteboardDao.kt` (+21行): `observeEdges/upsertEdge/deleteEdge/deleteEdgesForNode/countEdgeBetween`。
* `db/Migration10to11.kt` (新規): `whiteboard_edge` 作成＋索引。非破壊。
* `db/AppDatabase.kt`: `WhiteboardEdgeEntity` 登録、`version = 11`。
* `di/DatabaseModule.kt`: `MIGRATION_10_11` を追加。
* `app/schemas/.../11.json` (新規): 10.jsonから手書き派生。**ビルド時にRoomが再生成するので、差分が出たらそれをコミットする**
  （identityHashは生成値が正）。MigrationTestHelperの検証は構造比較なので手書き版でも通る。
* `repository/WhiteboardRepository.kt` (+35行): `observeEdges/addEdge/updateEdgeLabel/deleteEdge`。
  `addEdge` は自己ループ・既存ペア（向き不問）を弾いて null。`deleteNode` はエッジも同時掃除（孤児線防止）。
* `viewmodel/WhiteboardViewModel.kt` (+45行): `edges` フロー、接続モード（`linkSourceNodeId`／`startLink/cancelLink/completeLink`）、
  `setEdgeLabel/deleteEdge`、Toast用 `message`。`deleteNode` を Repo 経由に変更。
* `ui/screen/WhiteboardScreen.kt` (+163行):
  - `Canvas` でノード中心同士を `drawLine`（primary 75%・2dp・丸端）。座標系はノードと同じ内容px。
  - **ドラッグ追従**: `livePositions`（`mutableStateMapOf`）をドラッグ中に更新し、線が指に追従。DB反映後に破棄。
  - エッジ中点にチップ（ラベル or 🔗）→タップでラベル編集／線削除ダイアログ。
  - カード右下に🔗ボタン → 接続モード（起点カードが tertiaryContainer 色、上部にバナー＋取消）。
    次にタップしたカードへ線を張る。通常時のタップは従来通り entry 遷移。
  - TopBar件数に `🔗N` を併記。空状態ヒント文を更新。
* `db/DemoData.kt` (+11行): デモボードの先頭2枚に線（「転換点」「終了条件」）。
* `androidTest/.../MigrationTest.kt` (+44行): `migrate10To11_addsWhiteboardEdgeTable`、フルチェーンを v1→v11 に更新。

## 4. 検証

* kotlinc 単体構文検査: 新規記述由来のエラー0件（Compose/Room の未解決参照を除く）。
* **実機で確認すること**: (a) 既存DBが v10→v11 に無事上がる（ボード・ノード残存）、
  (b) 🔗→別カードで線が引ける、同じペアは「既に接続されています」、
  (c) ドラッグ中に線が追従し、離しても戻らない、(d) チップタップ→ラベル保存／線削除、
  (e) ノード削除で線も消える、(f) `connectedAndroidTest` で MigrationTest 緑。
* 3条件チェック（v15 §14）: ビルド ☐ / テスト ☐ / 3日実使用 ☐

## 5. 次の一手

* エッジの色分け（`colorHex` はカラムのみ）、connection への昇格ボタン。
* セクションのリサイズ・ノード割付（P1-1残）。
