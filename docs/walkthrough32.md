# walkthrough32 — Ktor CORS導入 (#S2)

**日付:** 2026-09-06
**コミット:** (本記録と同時)
**ビルド:** `compileDebugKotlin` BUILD SUCCESSFUL（警告は既存のみ、新規依存の解決に約90秒）
**根拠:** DESIGN §13 B1・§15 #S2

## 1. 背景

`ktor-server-cors` 未導入のため、LAN内ブラウザ（Vite dev等）からの
`Authorization` 付きfetchがプリフライトで失敗し得た。

## 2. 変更 (3ファイル)

* `gradle/libs.versions.toml` (+1行): `ktor-server-cors`（Ktor 3.5.2と同版）。
* `app/build.gradle.kts` (+1行): implementation追加。
* `server/LocalServer.kt` (+13行): `install(CORS)`（anyHost＋Authorization/Content-Type許可、
  OPTIONS/GET/POST/PATCH/DELETE許可）。Bearer方式は維持。

## 3. 検証

* コンパイル成功。実機では Vite(5173)→Ktor(8080)のPOST/PATCHが通ることを確認する。
* TLS化は範囲外（LAN警告UIが代替）。

## 4. 次の一手

* トークン暗号化（#S3）。
