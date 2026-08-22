package com.thuvstu.personalencyclopedia.perf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.thuvstu.personalencyclopedia.db.AppDatabase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Round 0 (M-1): SyntheticDataSeeder のトリガー。
 * debug ビルド（このレシーバー自体がdebugソースセット定義）からのみ到達可能。
 *
 * 使い方（adb）:
 *   50,000件投入:
 *     adb shell am broadcast -n com.thuvstu.personalencyclopedia/.perf.PerfSeedReceiver \
 *       -a com.thuvstu.personalencyclopedia.perf.SEED --ei count 50000
 *   1,000件 / 10,000件は count を変えるだけ。
 *   合成データ全削除:
 *     adb shell am broadcast -n com.thuvstu.personalencyclopedia/.perf.PerfSeedReceiver \
 *       -a com.thuvstu.personalencyclopedia.perf.CLEAR
 *
 * 進捗は Logcat タグ "SyntheticSeeder" に出る。完了時にToast表示。
 */
@AndroidEntryPoint
class PerfSeedReceiver : BroadcastReceiver() {

    @Inject lateinit var db: AppDatabase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SEED -> {
                val count = intent.getIntExtra(EXTRA_COUNT, DEFAULT_COUNT)
                // 50k件の投入はブロードキャスト制限時間を超えるため、プロセス生存中に継続する
                // コルーチンへ委ねる（アプリ起動中に実行することを前提とする）
                Toast.makeText(context, "合成データ $count 件の投入を開始…", Toast.LENGTH_SHORT).show()
                scope.launch {
                    val start = System.currentTimeMillis()
                    runCatching { SyntheticDataSeeder.seed(db, count) }
                        .onSuccess {
                            Toast.makeText(
                                context,
                                "合成データ $count 件 投入完了 (${(System.currentTimeMillis() - start) / 1000}s)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        .onFailure {
                            Toast.makeText(context, "投入失敗: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
            ACTION_CLEAR -> {
                scope.launch {
                    runCatching { SyntheticDataSeeder.clear(db) }
                        .onSuccess { Toast.makeText(context, "合成データを削除しました", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, "削除失敗: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }

    companion object {
        const val ACTION_SEED = "com.thuvstu.personalencyclopedia.perf.SEED"
        const val ACTION_CLEAR = "com.thuvstu.personalencyclopedia.perf.CLEAR"
        const val EXTRA_COUNT = "count"
        const val DEFAULT_COUNT = 1_000
    }
}
