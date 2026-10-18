package tw.hoonsor.ime.app.update

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import tw.hoonsor.ime.app.ui.dpi
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 新版對話框（鬧鐘 APP UpdateUi.kt 的框架元件版）：確認 → 檢查安裝權限 → 下載 → 交給系統安裝。
 * 所在的 Activity 要在 onResume 呼叫 [onResume]，從「安裝不明應用程式」設定頁回來才會自動開始下載。
 */
class UpdateDialog(
    private val activity: Activity,
    private val release: ReleaseInfo,
    private val onSkip: (() -> Unit)? = null,
    private val onClosed: () -> Unit = {},
) {
    private enum class Phase { CONFIRM, NEED_PERMISSION, DOWNLOADING, INSTALLING, ERROR }

    private val main = Handler(Looper.getMainLooper())
    private var phase = Phase.CONFIRM
    private var cancelled = AtomicBoolean(false)
    private var lastPercent = -2

    private val message = TextView(activity).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setLineSpacing(0f, 1.25f)
    }
    private val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        visibility = View.GONE
    }
    private val dialog: AlertDialog

    init {
        val pad = activity.dpi(22f)
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, activity.dpi(8f), pad, 0)
            addView(ScrollView(activity).apply { addView(message) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        // 按鈕先放佔位文字讓 AlertDialog 建立按鈕，show() 之後再依階段改文字與行為（避免按了自動關閉）
        dialog = AlertDialog.Builder(activity)
            .setTitle("有新版本 v${release.version}")
            .setView(body)
            .setPositiveButton(" ", null)
            .setNeutralButton(" ", null)
            .setNegativeButton(" ", null)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnCancelListener { close() }
    }

    fun show() {
        UpdateInstaller.onInstallError = { text ->
            if (phase == Phase.INSTALLING) showError(text)
        }
        dialog.show()
        render()
    }

    /** 從設定頁回來時，若已允許安裝就直接開始下載 */
    fun onResume() {
        if (phase == Phase.NEED_PERMISSION && UpdateInstaller.canInstall(activity)) start()
    }

    private fun start() {
        if (!UpdateInstaller.canInstall(activity)) {
            phase = Phase.NEED_PERMISSION
            render()
            return
        }
        phase = Phase.DOWNLOADING
        lastPercent = -2
        val flag = AtomicBoolean(false).also { cancelled = it }
        render()
        Thread {
            try {
                val apk = UpdateInstaller.download(activity.applicationContext, release, { flag.get() }) { p ->
                    val percent = if (p < 0f) -1 else (p * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        main.post { if (!flag.get()) showProgress(percent) }
                    }
                }
                if (flag.get()) return@Thread
                main.post { if (!flag.get()) { phase = Phase.INSTALLING; render() } }
                UpdateInstaller.install(activity.applicationContext, apk)
            } catch (e: UpdateInstaller.Cancelled) {
                // 使用者取消，對話框已關閉
            } catch (e: Exception) {
                main.post { if (!flag.get()) showError(e.message ?: e.javaClass.simpleName) }
            }
        }.apply { isDaemon = true; name = "app-update" }.start()
    }

    private fun showProgress(percent: Int) {
        if (phase != Phase.DOWNLOADING) return
        progress.isIndeterminate = percent < 0
        if (percent >= 0) progress.progress = percent
        message.text = if (percent >= 0) "下載中… $percent%" else "下載中…"
    }

    private fun showError(text: String) {
        phase = Phase.ERROR
        render()
        message.text = "$text\n\n也可以改用瀏覽器到 GitHub 下載 APK 手動安裝。"
    }

    private fun close() {
        cancelled.set(true)
        UpdateInstaller.onInstallError = null
        if (dialog.isShowing) dialog.dismiss()
        onClosed()
    }

    private fun button(which: Int, label: String?, onClick: () -> Unit = {}) {
        val b = dialog.getButton(which) ?: return
        if (label == null) {
            b.visibility = View.GONE
            return
        }
        b.visibility = View.VISIBLE
        b.text = label
        b.setOnClickListener { onClick() }
    }

    private fun render() {
        progress.visibility = if (phase == Phase.DOWNLOADING) View.VISIBLE else View.GONE
        when (phase) {
            Phase.CONFIRM -> {
                message.text = "目前版本 v${UpdateChecker.currentVersion}\n\n" +
                    release.notes.ifBlank { "（這個版本沒有附更新說明）" }
                button(DialogInterface.BUTTON_POSITIVE, "下載並更新") { start() }
                button(DialogInterface.BUTTON_NEUTRAL, if (onSkip != null) "略過此版" else null) { onSkip?.invoke(); close() }
                button(DialogInterface.BUTTON_NEGATIVE, "稍後") { close() }
            }
            Phase.NEED_PERMISSION -> {
                message.text = "第一次更新需要允許本 App「安裝不明應用程式」。\n\n" +
                    "按「前往設定」後打開開關，再按返回鍵回到這裡，就會自動開始下載。"
                button(DialogInterface.BUTTON_POSITIVE, "前往設定") { UpdateInstaller.openInstallPermissionSettings(activity) }
                button(DialogInterface.BUTTON_NEUTRAL, null)
                button(DialogInterface.BUTTON_NEGATIVE, "關閉") { close() }
            }
            Phase.DOWNLOADING -> {
                progress.isIndeterminate = true
                message.text = "下載中…"
                button(DialogInterface.BUTTON_POSITIVE, null)
                button(DialogInterface.BUTTON_NEUTRAL, null)
                button(DialogInterface.BUTTON_NEGATIVE, "取消") { close() }
            }
            Phase.INSTALLING -> {
                message.text = "已交給系統安裝，請在系統畫面按「更新」或「安裝」。\n\n" +
                    "安裝完成後字典與設定都會保留，下次叫出鍵盤就是新版。"
                button(DialogInterface.BUTTON_POSITIVE, null)
                button(DialogInterface.BUTTON_NEUTRAL, null)
                button(DialogInterface.BUTTON_NEGATIVE, "關閉") { close() }
            }
            Phase.ERROR -> {
                button(DialogInterface.BUTTON_POSITIVE, "用瀏覽器下載") {
                    val url = release.pageUrl.ifBlank { UpdateConfig.RELEASES_PAGE }
                    runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
                button(DialogInterface.BUTTON_NEUTRAL, null)
                button(DialogInterface.BUTTON_NEGATIVE, "關閉") { close() }
            }
        }
    }
}
