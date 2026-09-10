# walkthrough60 — 関連プロジェクト2件のgit submodule同梱

## 0. 背景
ユーザー要望により、関連プロジェクト2件をPersonalEncyclopediaリポジトリに「載せる」ことになった。
方法はgit submodule方式を選択（選択肢: submodule / subtree / 単純コピー / ドキュメントリンクのみ）。

## 1. 追加したsubmodule

| path | リポジトリ | 固定コミット(本セッション時点) | 内容 |
|---|---|---|---|
| `related/FablePKMENCY` | thuvstu/-FablePKMENCY- | `c0cd077` | Next.js + Drizzle + PG のHeptabase風ボードPKM(強化計画.md §2.1の調査対象)。最新コミット「PersonalEncyclopediaとの本格連携へ向けて」 |
| `related/reconstructing-autonomous-world-map` | thuvstu/reconstructing-autonomous-world-map | `3d2635c` | Vite + React + 世界地図データの再構築実験(admin1境界・国境・units-geo等のアセット同梱) |

- 記録は `.gitmodules` にURL・pathとも明記。clone後は `git submodule update --init --recursive` で展開。
- 各submoduleのHEAD追従は `git submodule update --remote` (または該当ディレクトリ内での通常pull+親側commit)。

## 2. ビルドへの影響確認
- Android: `settings.gradle.kts` は `include(":app")` のみ → `related/` はGradle対象外。影響なし。
- Web: `web/` 配下ではないため bun/vite の対象外。影響なし。
- `related/` は参考・再調査用であり、`docs/強化計画.md` の「スキーマ/アルゴリズムのみ採用・アーキテクチャは不採用」原則はそのまま有効。

## 3. ドキュメント
- `docs/強化計画.md` 付録「調査方法」に submodule同梱の1行を追記（再調査時の`git clone --depth 1`を省略できる旨）。

## 4. Git
- `related/FablePKMENCY` 追加 / `related/reconstructing-autonomous-world-map` 追加 / 本walkthrough、の3コミットで構成。
