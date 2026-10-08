package tw.hoonsor.ime.app

import android.content.Context
import android.content.Intent
import android.os.Build
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import tw.hoonsor.ime.app.keyboard.FanItem
import tw.hoonsor.ime.app.keyboard.FanOverlayView
import tw.hoonsor.ime.app.keyboard.KeyAction
import tw.hoonsor.ime.app.keyboard.KeyDef
import tw.hoonsor.ime.app.keyboard.KeyboardLayouts
import tw.hoonsor.ime.app.keyboard.KeyboardView
import tw.hoonsor.ime.app.panel.CandidateGridView
import tw.hoonsor.ime.app.panel.Overlay
import tw.hoonsor.ime.app.panel.PanelController
import tw.hoonsor.ime.app.panel.PanelMode
import tw.hoonsor.ime.app.panel.Tile
import tw.hoonsor.ime.app.panel.TileGridView
import tw.hoonsor.ime.app.settings.Prefs
import tw.hoonsor.ime.app.settings.SettingsActivity
import tw.hoonsor.ime.app.topbar.CandItem
import tw.hoonsor.ime.app.topbar.CandidateBarView
import tw.hoonsor.ime.app.topbar.ComposingBoxView
import tw.hoonsor.ime.app.topbar.ToolButton
import tw.hoonsor.ime.app.topbar.ToolbarView
import tw.hoonsor.ime.app.ui.Palette
import tw.hoonsor.ime.app.ui.dpi
import tw.hoonsor.ime.contracts.FieldType
import tw.hoonsor.ime.contracts.TextTarget
import tw.hoonsor.ime.zhuyin.Candidate
import tw.hoonsor.ime.zhuyin.CandidateSource
import tw.hoonsor.ime.zhuyin.DefaultZhuyinEngine
import tw.hoonsor.ime.zhuyin.EngineListener
import tw.hoonsor.ime.zhuyin.Lexicon
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

/**
 * 輸入法的大腦：持有引擎、送字目標、所有 View，處理所有按鍵與面板切換。
 *
 * 送字規則（ARCHITECTURE §5.1）：所有送字都經過 [currentTarget]，不直接呼叫 InputConnection。
 * 剪貼簿搜尋模式合併後，會暫時把 currentTarget 換成搜尋框。
 */
class ImeController(private val service: InputMethodService) : KeyboardView.Listener {

    private val ctx: Context = service
    private val prefs = Prefs(service)
    private val main = Handler(Looper.getMainLooper())
    private val store = UserPhraseStore(service)
    private val loader = Executors.newSingleThreadExecutor()

    private var system: Lexicon? = null
    private var engine: DefaultZhuyinEngine? = null
    private var loadNotified = false

    // ---------- 送字目標 ----------
    val imeTarget = ImeTextTarget({ service.currentInputConnection }, { service.currentInputEditorInfo })

    /** 所有送字都經過這裡（剪貼簿搜尋模式會替換）。 */
    var currentTarget: TextTarget = imeTarget
    private val editor = ImeEditor { service.currentInputConnection }

    // ---------- View ----------
    private lateinit var root: LinearLayout
    private lateinit var composingStrip: FrameLayout
    private lateinit var composingBox: ComposingBoxView
    lateinit var searchSlot: FrameLayout
        private set
    private lateinit var toolbar: ToolbarView
    private lateinit var candBar: CandidateBarView
    private lateinit var panelArea: FrameLayout
    private lateinit var keyboard: KeyboardView
    private lateinit var voiceSlot: FrameLayout
    private lateinit var clipboardSlot: FrameLayout
    private lateinit var candGrid: CandidateGridView
    private lateinit var editPanel: TileGridView
    private lateinit var gearMenu: TileGridView
    private lateinit var heightPanel: TileGridView
    private lateinit var fan: FanOverlayView
    private lateinit var navSpacer: View
    private lateinit var panels: PanelController
    private var viewReady = false

    // ---------- 狀態 ----------
    private enum class Layout { ZHUYIN, ENGLISH, SYMBOL }

    private var layout = Layout.ZHUYIN
    private var symbolSet = FanItem.FULL
    private var pinned = false
    private var shift = false
    private var assoc: List<Candidate> = emptyList()
    private var selStart = -1
    private var selEnd = -1
    private var shownItems: List<CandItem> = emptyList()

    // =====================================================================
    // 載入
    // =====================================================================

    /** 在背景載入系統詞庫與使用者詞庫，完成後建立引擎。 */
    fun loadAsync() {
        loader.execute {
            try {
                val t0 = System.currentTimeMillis()
                store.load()
                val lex = BufferedReader(InputStreamReader(ctx.assets.open("zhuyin_dict.tsv"), Charsets.UTF_8))
                    .use { Lexicon.loadTsv(it) }
                lex.textStartingWith("的") // 預先建立聯想索引
                Log.i(TAG, "lexicon loaded: ${lex.size} entries in ${System.currentTimeMillis() - t0} ms")
                main.post {
                    system = lex
                    buildEngine()
                }
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                main.post { toast("詞庫載入失敗：${e.message}") }
            }
        }
    }

    private fun buildEngine() {
        val lex = system ?: return
        val e = DefaultZhuyinEngine(lex, Lexicon(), object : EngineListener {
            override fun onUserPhraseCreated(text: String, readings: List<String>) = store.addPhrase(text, readings)
            override fun onPhraseUsed(text: String, readings: List<String>) {
                if (!currentTarget.hints.isPassword) store.recordUse(text, readings)
            }
            override fun onUserPhraseDeleted(text: String, readings: List<String>) = store.removePhrase(text, readings)
        })
        for (p in store.phrases()) {
            val src = if (p.source == "CONTACT") CandidateSource.CONTACT else CandidateSource.USER
            e.addUserPhrase(p.text, p.readings, src, p.freq)
        }
        for ((t, r, n) in store.usages()) e.setUsage(t, r, n)
        engine = e
        render()
    }

    // =====================================================================
    // View 建立（只建立一次）
    // =====================================================================

    fun createView(): View {
        val c = ctx
        root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        composingStrip = FrameLayout(c)
        composingBox = ComposingBoxView(c)
        composingStrip.addView(composingBox, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(composingStrip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dpi(COMPOSING_DP)))

        searchSlot = FrameLayout(c).apply { visibility = View.GONE; setBackgroundColor(Palette.BG) }
        root.addView(searchSlot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val topBar = FrameLayout(c).apply { setBackgroundColor(Palette.BG) }
        toolbar = ToolbarView(c).apply { onTap = ::onToolbar }
        candBar = CandidateBarView(c).apply {
            onPick = ::pick
            onLongPick = ::longPick
            onExpand = { if (shownItems.isNotEmpty()) toggleOverlay(Overlay.CANDIDATES) }
            visibility = View.GONE
        }
        topBar.addView(toolbar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        topBar.addView(candBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dpi(TOPBAR_DP)))

        panelArea = FrameLayout(c).apply { setBackgroundColor(Palette.BG) }
        val match = { FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        keyboard = KeyboardView(c).also { it.listener = this }
        voiceSlot = FrameLayout(c).apply { visibility = View.GONE }
        clipboardSlot = FrameLayout(c).apply { visibility = View.GONE }
        candGrid = CandidateGridView(c).apply { onPick = { i -> showOverlay(Overlay.NONE); pick(i) } }
        editPanel = TileGridView(c).apply { columns = 5; onTap = ::onEditTile; onLongPress = ::onEditLong }
        gearMenu = TileGridView(c).apply {
            columns = 3
            tiles = listOf(Tile("settings", "參數設定", "⚙"), Tile("height", "高度調整", "↕"), Tile("phrases", "常用短語", "⚡"))
            onTap = ::onGearTile
        }
        heightPanel = TileGridView(c).apply {
            columns = 3
            tiles = listOf(Tile("taller", "較高", "▲"), Tile("shorter", "較低", "▼"), Tile("done", "完成", "✓"))
            onTap = ::onHeightTile
        }
        fan = FanOverlayView(c).apply {
            onSelect = { item -> showOverlay(Overlay.NONE); onFanItem(item) }
            onDismiss = { showOverlay(Overlay.NONE) }
        }
        panelArea.addView(keyboard, match())
        panelArea.addView(voiceSlot, match())
        panelArea.addView(clipboardSlot, match())
        panelArea.addView(candGrid, match())
        panelArea.addView(editPanel, match())
        // 齒輪選單：鍵盤左上方約 82% 寬、上半部（依倬截圖）
        panelArea.addView(gearMenu, FrameLayout.LayoutParams(0, c.dpi(136f), Gravity.TOP or Gravity.START))
        panelArea.addView(heightPanel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dpi(110f), Gravity.TOP))
        panelArea.addView(fan, match())
        root.addView(panelArea, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dpi(prefs.panelHeightDp.toFloat())))

        navSpacer = View(c).apply { setBackgroundColor(Palette.BG) }
        root.addView(navSpacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))

        // Android 15+：底部手勢列 inset，避免底列按鍵被遮住（K15）
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bottom = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            else @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            val lp = navSpacer.layoutParams
            if (lp.height != bottom) { lp.height = bottom; navSpacer.layoutParams = lp }
            insets
        }
        panelArea.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val lp = gearMenu.layoutParams
            val w = ((r - l) * 0.82f).toInt()
            if (lp.width != w) { lp.width = w; gearMenu.layoutParams = lp }
        }

        panels = PanelController(panelArea, keyboard, voiceSlot, clipboardSlot, mapOf(
            Overlay.CANDIDATES to candGrid,
            Overlay.EDIT to editPanel,
            Overlay.GEAR to gearMenu,
            Overlay.HEIGHT to heightPanel,
            Overlay.FAN to fan,
        ))
        panels.showPanel(PanelMode.KEYBOARD)
        viewReady = true
        applyLayout()
        render()
        return root
    }

    // =====================================================================
    // 生命週期
    // =====================================================================

    fun onStartInputView(info: EditorInfo?) {
        engine?.reset()
        assoc = emptyList()
        currentTarget = imeTarget
        editor.selecting = false
        shift = false
        pinned = false
        if (!viewReady) return
        val hints = ImeTextTarget.hintsOf(info)
        layout = when {
            hints.isPassword -> Layout.ENGLISH
            hints.fieldType == FieldType.NUMBER || hints.fieldType == FieldType.PHONE -> {
                symbolSet = FanItem.NUM; pinned = true; Layout.SYMBOL
            }
            hints.fieldType == FieldType.EMAIL || hints.fieldType == FieldType.URI -> Layout.ENGLISH
            else -> Layout.ZHUYIN
        }
        keyboard.haptics = prefs.haptics
        toolbar.buttons = ToolButton.entries.filter { it != ToolButton.CLIPBOARD || prefs.toolbarClipboard }
        fan.items = FanItem.entries.filter { it != FanItem.CLIPBOARD || prefs.fanClipboard }
        panels.setHeight(ctx.dpi(prefs.panelHeightDp.toFloat()))
        panels.showPanel(PanelMode.KEYBOARD)
        applyLayout()
        render()
    }

    fun onFinishInputView() {
        engine?.reset()
        assoc = emptyList()
        if (viewReady) {
            showOverlay(Overlay.NONE)
            render()
        }
    }

    fun onSelectionChanged(newStart: Int, newEnd: Int) {
        selStart = newStart
        selEnd = newEnd
        if (viewReady && panels.overlay == Overlay.EDIT) refreshEditTiles()
    }

    /** 組字框區是透明的，不能把 App 內容往上推，也不攔截觸控。 */
    fun adjustInsets(out: InputMethodService.Insets) {
        if (!viewReady || !root.isShown) return
        val strip = composingStrip.height
        out.contentTopInsets += strip
        out.visibleTopInsets += strip
        out.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT
    }

    fun release() {
        store.saveNow()
        loader.shutdown()
    }

    // =====================================================================
    // 畫面更新
    // =====================================================================

    private fun applyLayout() {
        if (!viewReady) return
        keyboard.shiftOn = shift
        keyboard.setRows(when (layout) {
            Layout.ZHUYIN -> KeyboardLayouts.ZHUYIN
            Layout.ENGLISH -> KeyboardLayouts.english(shift, prefs.numberRow)
            Layout.SYMBOL -> KeyboardLayouts.symbols(symbolSet, pinned)
        })
    }

    private fun render() {
        if (!viewReady) return
        val st = engine?.state
        val composing = st?.isComposing == true
        composingBox.setSegments(if (composing) st!!.segments else emptyList())
        shownItems = when {
            composing -> st!!.candidates.take(MAX_BAR).mapIndexed { i, c ->
                CandItem(c.text, highlight = i == 0, badge = when (c.source) {
                    CandidateSource.USER -> "U"; CandidateSource.CONTACT -> "C"; else -> null
                })
            }
            assoc.isNotEmpty() -> assoc.map { CandItem(it.text) }
            else -> emptyList()
        }
        val showCands = shownItems.isNotEmpty()
        candBar.visibility = if (showCands) View.VISIBLE else View.GONE
        toolbar.visibility = if (showCands) View.GONE else View.VISIBLE
        candBar.setChip(null) // 剪貼簿 chip：合併後由 ClipboardController.recentClip 提供（R2）
        candBar.setItems(shownItems)
        if (panels.overlay == Overlay.CANDIDATES) {
            if (showCands) candGrid.setItems(allItems()) else showOverlay(Overlay.NONE)
        }
    }

    private fun allItems(): List<CandItem> {
        val st = engine?.state ?: return shownItems
        return if (st.isComposing) st.candidates.mapIndexed { i, c -> CandItem(c.text, highlight = i == 0) } else shownItems
    }

    private fun showOverlay(o: Overlay) {
        panels.showOverlay(o)
        syncToolbarActive()
    }

    private fun toggleOverlay(o: Overlay) {
        if (o == Overlay.CANDIDATES) candGrid.setItems(allItems())
        if (o == Overlay.EDIT) refreshEditTiles()
        panels.toggleOverlay(o)
        syncToolbarActive()
    }

    private fun syncToolbarActive() {
        toolbar.setActive(ToolButton.SETTINGS, panels.overlay == Overlay.GEAR || panels.overlay == Overlay.HEIGHT)
        toolbar.setActive(ToolButton.EDIT, panels.overlay == Overlay.EDIT)
        toolbar.setActive(ToolButton.CLIPBOARD, panels.mode == PanelMode.CLIPBOARD)
        toolbar.setActive(ToolButton.MIC, panels.mode == PanelMode.VOICE)
    }

    // =====================================================================
    // 送字
    // =====================================================================

    private fun commit(text: String) {
        currentTarget.commitText(text)
        assoc = if (layout == Layout.ZHUYIN && prefs.associations && !currentTarget.hints.isPassword) {
            engine?.associations(text, 20) ?: emptyList()
        } else emptyList()
    }

    /** 把組字中的內容以第一個候選送出（標點、Enter、切換鍵盤前呼叫）。 */
    private fun commitComposing() {
        val e = engine ?: return
        var guard = 0
        while (e.state.isComposing && guard++ < 20) {
            if (e.state.candidates.isEmpty()) {
                currentTarget.commitText(e.state.composing)
                e.reset()
                break
            }
            e.select(0).commit?.let { currentTarget.commitText(it) }
        }
        assoc = emptyList()
    }

    private fun pick(i: Int) {
        val e = engine ?: return
        if (e.state.isComposing) {
            val r = e.select(i)
            r.commit?.let { commit(it) }
        } else {
            val a = assoc.getOrNull(i) ?: return
            commit(a.text)
        }
        render()
    }

    /** 長按使用者詞 → 刪除（Z6）。 */
    private fun longPick(i: Int) {
        val e = engine ?: return
        val c = e.state.candidates.getOrNull(i) ?: return
        if (!e.state.isComposing || c.source == CandidateSource.SYSTEM) return
        if (e.deleteUserPhrase(c)) toast("已刪除自建詞「${c.text}」")
        render()
    }

    // =====================================================================
    // 鍵盤事件
    // =====================================================================

    override fun onKey(key: KeyDef) {
        val e = engine
        when (val a = key.action) {
            is KeyAction.Zhuyin -> {
                if (e == null) { notifyLoading(); return }
                assoc = emptyList()
                e.onSymbol(a.c)
            }
            is KeyAction.Commit -> {
                if (e?.state?.isComposing == true) commitComposing()
                currentTarget.commitText(a.text)
                assoc = emptyList()
                if (layout == Layout.SYMBOL && !pinned) { layout = Layout.ZHUYIN; applyLayout() }
                if (layout == Layout.ENGLISH && shift) { shift = false; applyLayout() }
            }
            KeyAction.Space -> {
                if (e?.state?.isComposing == true) e.onSymbol(' ')
                else { assoc = emptyList(); currentTarget.commitText(" ") }
            }
            KeyAction.Backspace -> {
                if (e?.state?.isComposing == true) e.onBackspace()
                else { assoc = emptyList(); currentTarget.deleteSurroundingText(1, 0) }
            }
            KeyAction.Enter -> {
                if (e?.state?.isComposing == true) commitComposing()
                else {
                    assoc = emptyList()
                    if (currentTarget === imeTarget) imeTarget.enter() else currentTarget.commitText("\n")
                }
            }
            KeyAction.Shift -> { shift = !shift; applyLayout() }
            KeyAction.ToEnglish -> { commitComposing(); layout = Layout.ENGLISH; applyLayout() }
            KeyAction.ToZhuyin -> { layout = Layout.ZHUYIN; pinned = false; applyLayout() }
            KeyAction.Pin -> { pinned = !pinned; applyLayout() }
            KeyAction.Fan, KeyAction.Punct -> {} // 由手勢回呼處理
        }
        render()
    }

    override fun onPunct(text: String) {
        commitComposing()
        currentTarget.commitText(text)
        assoc = emptyList()
        render()
    }

    override fun onFanStart(cx: Float, cy: Float) {
        panels.showOverlay(Overlay.FAN)
        fan.open(cx, cy)
    }

    override fun onFanMove(x: Float, y: Float) = fan.track(x, y)

    override fun onFanEnd(x: Float, y: Float, dragged: Boolean) = fan.release(x, y, dragged)

    private fun onFanItem(item: FanItem) {
        if (item == FanItem.CLIPBOARD) {
            toast("剪貼簿模組尚未合併")
            return
        }
        commitComposing()
        symbolSet = item
        pinned = false
        layout = Layout.SYMBOL
        applyLayout()
        render()
    }

    // =====================================================================
    // 工具列、齒輪選單、高度、編輯面板
    // =====================================================================

    private fun onToolbar(b: ToolButton) {
        when (b) {
            ToolButton.SETTINGS -> toggleOverlay(Overlay.GEAR)
            ToolButton.MIC -> toast("語音模組尚未合併")
            ToolButton.TRAD_SIMP -> toast("繁簡切換尚未實作")
            ToolButton.LEFT -> { commitComposing(); editor.selecting = false; editor.left() }
            ToolButton.RIGHT -> { commitComposing(); editor.selecting = false; editor.right() }
            ToolButton.CLIPBOARD -> toast("剪貼簿模組尚未合併")
            ToolButton.EDIT -> toggleOverlay(Overlay.EDIT)
            ToolButton.HIDE -> { showOverlay(Overlay.NONE); service.requestHideSelf(0) }
        }
    }

    private fun onGearTile(t: Tile) {
        when (t.id) {
            "settings" -> {
                showOverlay(Overlay.NONE)
                ctx.startActivity(Intent(ctx, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "height" -> showOverlay(Overlay.HEIGHT)
            "phrases" -> { showOverlay(Overlay.NONE); toast("常用短語尚未實作") }
        }
    }

    private fun onHeightTile(t: Tile) {
        when (t.id) {
            "taller" -> prefs.panelHeightDp = (prefs.panelHeightDp + 10).coerceAtMost(Prefs.MAX_HEIGHT_DP)
            "shorter" -> prefs.panelHeightDp = (prefs.panelHeightDp - 10).coerceAtLeast(Prefs.MIN_HEIGHT_DP)
            "done" -> { showOverlay(Overlay.NONE); return }
        }
        panels.setHeight(ctx.dpi(prefs.panelHeightDp.toFloat()))
    }

    private val hasSelection get() = editor.selecting || (selStart >= 0 && selStart != selEnd)

    private fun refreshEditTiles() {
        val sel = hasSelection
        editPanel.tiles = listOf(
            if (sel) Tile("copy", "複製", "⧉") else Tile("startSel", "開選", "I"),
            Tile("home", "行首", "⇤"), Tile("up", "上移", "↑"), Tile("end", "行尾", "⇥"),
            if (sel) Tile("ai1", "潤飾", "✦", true) else Tile("ai1", "潤飾全文", "✦", true),
            Tile("cut", "剪下", "✂"), Tile("left", "左移", "←"),
            if (sel) Tile("stopSel", "停選", "✕") else Tile("selAll", "全選", "▤"),
            Tile("right", "右移", "→"),
            if (sel) Tile("ai2", "正式", "✦", true) else Tile("ai2", "AI 寫作", "✦", true),
            Tile("paste", "貼上", "⎘"), Tile("delBack", "後刪", "⌫"), Tile("down", "下移", "↓"), Tile("delFwd", "前刪", "⌦"),
            if (sel) Tile("ai3", "翻英", "✦", true) else Tile("ai3", "翻英全文", "✦", true),
        )
    }

    private fun onEditTile(t: Tile) {
        when (t.id) {
            "startSel" -> editor.startSelect()
            "selAll" -> editor.selectAll()
            "stopSel" -> editor.stopSelect(selEnd)
            "copy" -> { editor.copy(); toast("已複製") }
            "cut" -> editor.cut()
            "paste" -> editor.paste()
            "home" -> editor.lineStart()
            "end" -> editor.lineEnd()
            "up" -> editor.up()
            "down" -> editor.down()
            "left" -> editor.left()
            "right" -> editor.right()
            "delBack" -> editor.deleteBackward()
            "delFwd" -> editor.deleteForward()
            "ai1", "ai2", "ai3" -> toast("AI 改寫模組尚未合併（REQUESTS R1）")
        }
        refreshEditTiles()
    }

    /** 長按「貼上」→ 剪貼簿面板（合併後）。 */
    private fun onEditLong(t: Tile) {
        if (t.id == "paste") toast("剪貼簿模組尚未合併")
    }

    // =====================================================================

    private fun notifyLoading() {
        if (!loadNotified) { toast("詞庫載入中，請稍候…"); loadNotified = true }
    }

    private fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG = "ImeController"
        const val COMPOSING_DP = 32f
        const val TOPBAR_DP = 52f
        private const val MAX_BAR = 40
    }
}
