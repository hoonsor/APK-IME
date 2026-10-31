package tw.hoonsor.ime.app.ai

import android.content.Context
import android.os.SystemClock
import android.util.Log
import tw.hoonsor.ime.contracts.SecretProvider
import tw.hoonsor.vocab.VocabClient
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 語音個人詞庫中心的單一入口：整個 App 共用一份快取。
 * 同步一律在背景執行緒，失敗只記 log；鍵盤與語音只讀本機快取，絕不等網路。
 */
object VocabHub {
    private const val TAG = "VocabHub"
    private const val MIN_INTERVAL_MS = 5 * 60_000L

    private val io = Executors.newSingleThreadExecutor()
    private val syncing = AtomicBoolean(false)
    @Volatile private var client: VocabClient? = null
    @Volatile private var lastAttempt = 0L

    fun get(context: Context): VocabClient =
        client ?: synchronized(this) {
            client ?: run {
                val app = context.applicationContext
                val secrets = KeystoreSecretStore(app)
                VocabClient(VocabClient.DEFAULT_BASE_URL, "ime", app.filesDir) { secrets.getBlocking(SecretProvider.VOCAB_HUB) }
                    .also { client = it }
            }
        }

    /** 鍵盤每次出現時呼叫；5 分鐘內最多同步一次。 */
    fun syncIfStale(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (lastAttempt != 0L && now - lastAttempt < MIN_INTERVAL_MS) return
        syncNow(context, null)
    }

    fun syncNow(context: Context, done: ((VocabClient.SyncResult?) -> Unit)?) {
        if (!syncing.compareAndSet(false, true)) {
            done?.invoke(null)
            return
        }
        lastAttempt = SystemClock.elapsedRealtime()
        io.execute {
            var result: VocabClient.SyncResult? = null
            try {
                result = get(context).sync()
                result.error?.let { Log.i(TAG, "sync: $it") }
            } catch (e: Exception) {
                Log.w(TAG, "sync failed", e)
            } finally {
                syncing.set(false)
            }
            done?.invoke(result)
        }
    }

    fun runIo(block: () -> Unit) = io.execute {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "io failed", e)
        }
    }
}
