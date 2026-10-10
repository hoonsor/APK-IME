package tw.hoonsor.ime.voice.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** 錄音參數與靜音判斷（MOBILE_VOICE_HANDOFF §4，桌面版已驗證；門檻需在手機微調）。 */
object AudioRules {
    const val SAMPLE_RATE = 16_000
    const val FRAME_SAMPLES = SAMPLE_RATE / 20        // 50 ms
    const val SILENCE_RMS = 0.008f
    const val MIN_VOICED_FRAMES = 4                    // < 0.2 秒有聲 → 視為靜音
    const val MAX_SECONDS = 180

    fun frameRms(buf: ShortArray, n: Int): Float {
        var sum = 0.0
        for (i in 0 until n) { val s = buf[i] / 32768.0; sum += s * s }
        return if (n == 0) 0f else sqrt(sum / n).toFloat()
    }

    fun levelOf(rms: Float): Float = (rms / 0.2f).coerceIn(0f, 1f)

    /** 回傳 null 表示靜音；否則回傳要保留的框範圍（頭尾各留 300 ms）。 */
    fun voicedRange(frames: List<Float>): IntRange? {
        if (frames.count { it >= SILENCE_RMS } < MIN_VOICED_FRAMES) return null
        val first = frames.indexOfFirst { it >= SILENCE_RMS }
        val last = frames.indexOfLast { it >= SILENCE_RMS }
        val pad = 6
        return (first - pad).coerceAtLeast(0)..(last + pad).coerceAtMost(frames.lastIndex)
    }

    /** PCM16 mono → WAV。 */
    fun wav(pcm: ByteArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }.array()
        return header + pcm
    }
}

/**
 * AudioRecord 錄音（VOICE_RECOGNITION、16 kHz mono PCM16），50 ms 一框計算 RMS。
 * 在自己的執行緒讀取；[onFrame] 在錄音執行緒上被呼叫。
 */
class Recorder(private val onFrame: (elapsedMs: Long, level: Float) -> Unit) {

    class Result(val pcm: ByteArray, val frames: List<Float>, val peakRms: Float)

    @Volatile private var running = false
    private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()
    private val frames = ArrayList<Float>()
    private var peak = 0f
    private var error: Exception? = null

    /** 呼叫前需確認已有麥克風權限。失敗時拋出例外。 */
    @SuppressLint("MissingPermission")
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(AudioRules.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, AudioRules.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, AudioRules.FRAME_SAMPLES * 2 * 4),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("麥克風無法啟動（可能被其他 App 使用中）")
        }
        rec.startRecording()
        running = true
        pcm.reset(); frames.clear(); peak = 0f; error = null
        thread = Thread({
            val buf = ShortArray(AudioRules.FRAME_SAMPLES)
            val bytes = ByteBuffer.allocate(AudioRules.FRAME_SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN)
            val t0 = System.currentTimeMillis()
            try {
                while (running) {
                    var n = 0
                    while (n < buf.size && running) {
                        val r = rec.read(buf, n, buf.size - n)
                        if (r < 0) throw IllegalStateException("錄音讀取失敗 ($r)")
                        n += r
                    }
                    if (n == 0) continue
                    bytes.clear()
                    for (i in 0 until n) bytes.putShort(buf[i])
                    synchronized(this) { pcm.write(bytes.array(), 0, n * 2) }
                    val rms = AudioRules.frameRms(buf, n)
                    synchronized(this) { frames += rms }
                    if (rms > peak) peak = rms
                    val elapsed = System.currentTimeMillis() - t0
                    onFrame(elapsed, AudioRules.levelOf(rms))
                    if (elapsed > AudioRules.MAX_SECONDS * 1000L) running = false
                }
            } catch (e: Exception) {
                error = e
                Log.w("Recorder", "record error", e)
            } finally {
                try { rec.stop() } catch (_: Exception) {}
                rec.release()
            }
        }, "voice-recorder").also { it.start() }
    }

    val isRunning get() = running

    /** 停止並取得錄音（會等待錄音執行緒結束，請勿在主執行緒呼叫）。 */
    fun stop(): Result {
        running = false
        thread?.join(2_000)
        thread = null
        error?.let { if (frames.isEmpty()) throw it }
        synchronized(this) { return Result(pcm.toByteArray(), frames.toList(), peak) }
    }

    /** 取消（不等待）。 */
    fun cancel() {
        running = false
    }
}
