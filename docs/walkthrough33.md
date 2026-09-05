# walkthrough33 — サーバートークン暗号化 (#S3)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `compileDebugKotlin` BUILD SUCCESSFUL
**根拠:** DESIGN §13 B2・§15 #S3

## 1. 背景

Gemini/StudyPlus鍵が暗号化なのに、サーバートークンだけ平文DataStore＋PC側localStorage平文だった。
Android側の保管を暗号化する（PC側は運用注意のまま）。

## 2. 変更 (1ファイル)

* `server/TokenManager.kt` (全面書換):
  - `secure_settings` EncryptedSharedPreferences（SettingsRepositoryと同一ファイル・同一方式）に移行。
  - 旧 `server_prefs` に残る平文トークンは初回に移行して削除（ワンタイム）。
  - `tokenFlow: StateFlow` は維持（ServerViewModelの型互換のため内部MutableStateFlowでミラー）。

## 3. 検証

* コンパイル成功。実機では既存トークンの引き継ぎ（再発行不要なこと）と
  設定画面の表示・コピー・再発行を確認する。

## 4. 次の一手

* Web未配線の解消（#W1: 接続・候補・ヒートマップ）。
