package tw.hoonsor.ime.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import tw.hoonsor.ime.app.clipboard.ClipSearchStrip
import tw.hoonsor.ime.app.clipboard.ClipboardHistory
import tw.hoonsor.ime.app.clipboard.SearchTarget
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
import tw.hoonsor.ime.app.panel.RowListView
import tw.hoonsor.ime.app.panel.Tile
import tw.hoonsor.ime.app.panel.TileGridView
import tw.hoonsor.ime.app.settings.DictionaryActivity
import tw.hoonsor.ime.app.settings.Prefs
import tw.hoonsor.ime.app.settings.SettingsActivity
import tw.hoonsor.ime.app.text.ChineseConverter
import tw.hoonsor.ime.app.topbar.CandItem
import tw.hoonsor.ime.app.topbar.CandidateBarView
import tw.hoonsor.ime.app.topbar.ComposingBoxView
import tw.hoonsor.ime.app.topbar.SpecialChip
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
import java.util.concurrent.Executors

/**
 * 輸入法的大腦：持有引擎、送字目標、所有 View，處理所有按鍵與面板切換。
 *
 * 送字規則（ARCHITECTURE §5.1）：所有送字都經過 [currentTarget]，不直接呼叫 InputConnection。
 * 剪貼簿搜尋模式時，currentTarget 會暫時換成搜尋框（[SearchTarget]）。
 */
class ImeController(private val service: InputMethodService) : KeyboardView.Listener {

    private val ctx: Context = service
    private val prefs = Prefs(service)
    private val main = Handler(Looper.getMainLooper())
    private val store = UserPhraseStore.get(service)
    private val clips = ClipboardHistory.get(service)
    private val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val loader = Executors.newSingleThreadExecutor()

    private var system: Lexicon? = null
    private var engine: DefaultZhuyinEngine? = null
    private var loadNotified = false
    private var syncedExternal = -1

    // ---------- 送字目標 ----------
    val imeTarget = ImeTextTarget({ service.currentInputConnection }, { service.currentInputEditorInfo })

    /** 所有送字都經過這裡（剪貼簿搜尋模式會替換）。 */
    var currentTarget: TextTarget = imeTarget
    private val editor = ImeEditor { service.currentInputConnection }
    private val searchTarget = SearchTarget { q -> onSearchChange(q) }
    private var searching = false

    // ---------- View ----------
    private lateinit var root: LinearLayout
    private lateinit var composingStrip: FrameLayout
    private lateinit var composingBox: ComposingBoxView
    lateinit var searchSlot: FrameLayout
        private set
    private lateinit var searchStrip: ClipSearchStrip
    private lateinit var toolbar: ToolbarView
    private lateinit var candBar: CandidateBarView
    private lateinit var panelArea: FrameLayout
    private lateinit var keyboard: KeyboardView
    private lateinit var voiceSlot: FrameLayout
    private lateinit var clipboardSlot: FrameLayout
    private lateinit var clipPanel: RowListView
    private lateinit var phrasePanel: RowListView
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

    /** 候選列目前顯示的是什麼（決定點選後要做什麼）。 */
    private enum class BarKind { NONE, CANDIDATES, ASSOC, WORD, DELETE }

    private var layout = Layout.ZHUYIN
    private var symbolSet = FanItem.FULL
    private var pinned = false
    private var shiftState = 0          // 0 小寫、1 下一字大寫、2 大寫鎖定
    private var lastShiftTap = 0L
    private var assoc: List<Candidate> = emptyList()
    private var selStart = -1
    private var selEnd = -1
    private var shownItems: List<CandItem> = emptyList()
    private var barKind = BarKind.NONE

    /** 需求七：連續輸入的英文字母＋符號（例如 abc@gmail.com），可一鍵加入字典。 */
    private val wordBuf = StringBuilder()
    private var wordMatches: List<String> = emptyList()

    /** 需求六：長按自建詞後等待確認刪除。 */
    private var pendingDelete: Candidate? = null
    private var confirmClear = false

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (prefs.clipCapture && !isSensitiveClip()) clips.captureFrom(cm, prefs.clipMax)
    }
    private val clipsChanged: () -> Unit = {
        if (viewReady) {
            if (panels.mode == PanelMode.CLIPBOARD) refreshClipPanel()
            render()
        }
    }

    // =====================================================================
    // 載入
    // =====================================================================

    /** 在背景載入系統詞庫與使用者資料，完成後建立引擎。 */
    fun loadAsync() {
        cm.addPrimaryClipChangedListener(clipListener)
        clips.listeners += clipsChanged
        loader.execute {
            try {
                store.load()
                clips.load()
                val lex = LexiconHolder.get(ctx)
                lex.textStartingWith("的")   // 預先建立聯想索引
                lex.readingsFor("的")         // 預先建立反查注音索引（納入字典用）
                ChineseConverter.ensureLoaded(ctx)
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
        engine = e
        loadUserPhrases(e)
        for ((t, r, n) in store.usages()) e.setUsage(t, r, n)
        render()
    }

    private fun loadUserPhrases(e: DefaultZhuyinEngine) {
        e.clearUserPhrases()
        for (p in store.phrases()) {
            val src = if (p.source == "CONTACT") CandidateSource.CONTACT else CandidateSource.USER
            e.addUserPhrase(p.text, p.readings, src, p.freq)
        }
        syncedExternal = store.externalVersion
    }

    // =====================================================================
    // View 建立（只建立一次）
    // =====================================================================

    fun createView(): View {
        val c = ctx
        val match = { FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        root = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        composingStrip = FrameLayout(c)
        composingBox = ComposingBoxView(c)
        composingStrip.addView(composingBox, match())
        root.addView(composingStrip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dpi(COMPOSING_DP)))

        searchSlot = FrameLayout(c).apply { visibility = View.GONE; setBackgroundColor(Palette.BG) }
        searchStrip = ClipSearchStrip(c).apply {
            onPick = { text -> exitSearch(backToPanel = false); pasteClip(text) }
            onClose = { exitSearch(backToPanel = true) }
        }
        searchSlot.addView(searchStrip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dpi(52f)))
        root.addView(searchSlot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val topBar = FrameLayout(c).apply { setBackgroundColor(Palette.BG) }
        toolbar = ToolbarView(c).apply { onTap = ::onToolbar }
        candBar = CandidateBarView(c).apply {
            onPick = ::pick
            onLongPick = ::longPick
            onExpand = { if (barKind == BarKind.CANDIDATES) toggleOverlay(Overlay.CANDIDATES) }
            onChipClick = { clips.recent?.let { pasteClip(it.text) } }
            visibility = View.GONE
        }
        topBar.addView(toolbar, match())
        topBar.addView(candBar, match())
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, c.dpi(TOPBAR_DP)))

        panelArea = FrameLayout(c).apply { setBackgroundColor(Palette.BG) }
        keyboard = KeyboardView(c).also { it.listener = this }
        voiceSlot = FrameLayout(c).apply { visibility = View.GONE }
        clipboardSlot = FrameLayout(c).apply { visibility = View.GONE }
        clipPanel = RowListView(c)
        clipboardSlot.addView(clipPanel, match())
        phrasePanel = RowListView(c)
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
        panelArea.addView(phrasePanel, match())
        // 齒輪選單：鍵盤左上方約 82% 寬（依倬截圖）
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
            Overlay.PHRASES to phrasePanel,
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
        engine?.let { if (store.externalVersion != syncedExternal) loadUserPhrases(it) }
        assoc = emptyList()
        currentTarget = imeTarget
        searching = false
        editor.selecting = false
        shiftState = 0
        pinned = false
        wordBuf.setLength(0)
        pendingDelete = null
        confirmClear = false
        if (prefs.clipCapture && !isSensitiveClip()) clips.captureFrom(cm, prefs.clipMax)
        if (!viewReady) return
        searchSlot.visibility = View.GONE
        val hints = ImeTextTarget.hintsOf(info)
        layout = when {
            hints.isPassword -> Layout.ENGLISH
            hints.fieldType == FieldType.NUMBER || hints.fieldType == FieldType.PHONE -> {
                symbolSet = FanItem.NUM; Layout.SYMBOL
            }
            hints.fieldType == FieldType.EMAIL || hints.fieldType == FieldType.URI -> Layout.ENGLISH
            else -> Layout.ZHUYIN
        }
        keyboard.haptics = prefs.haptics
        toolbar.simplified = prefs.simplified
        toolbar.buttons = ToolButton.entries.filter { it != ToolButton.CLIPBOARD || prefs.toolbarClipboard }
        fan.items = KeyboardLayouts.FAN_ITEMS.filter { it != FanItem.CLIPBOARD || prefs.fanClipboard }
        panels.setHeight(ctx.dpi(prefs.panelHeightDp.toFloat()))
        panels.showPanel(PanelMode.KEYBOARD)
        applyLayout()
        render()
    }

    fun onFinishInputView() {
        engine?.reset()
        assoc = emptyList()
        wordBuf.setLength(0)
        pendingDelete = null
        if (searching) {
            searching = false
            currentTarget = imeTarget
        }
        if (viewReady) {
            searchSlot.visibility = View.GONE
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
        cm.removePrimaryClipChangedListener(clipListener)
        clips.listeners -= clipsChanged
        store.saveNow()
        clips.saveNow()
        loader.shutdown()
    }

    // =====================================================================
    // 畫面更新
    // =====================================================================

    private val symbolSticky get() = pinned || symbolSet == FanItem.NUM

    private fun applyLayout() {
        if (!viewReady) return
        keyboard.shiftState = shiftState
        keyboard.setRows(when (layout) {
            Layout.ZHUYIN -> KeyboardLayouts.ZHUYIN
            Layout.ENGLISH -> KeyboardLayouts.english(shiftState > 0, prefs.numberRow)
            Layout.SYMBOL -> if (symbolSet == FanItem.NUM) KeyboardLayouts.DIALER else KeyboardLayouts.symbols(symbolSet, pinned)
        })
    }

    private fun render() {
        if (!viewReady) return
        val st = engine?.state
        val composing = st?.isComposing == true
        composingBox.setSegments(if (composing) st!!.segments else emptyList())

        val pd = pendingDelete
        barKind = when {
            panels.mode != PanelMode.KEYBOARD -> BarKind.NONE
            pd != null -> BarKind.DELETE
            composing -> BarKind.CANDIDATES
            wordBuf.isNotEmpty() && layout != Layout.ZHUYIN -> BarKind.WORD
            assoc.isNotEmpty() -> BarKind.ASSOC
            else -> BarKind.NONE
        }
        shownItems = when (barKind) {
            BarKind.DELETE -> listOf(
                CandItem("刪除「${pd!!.text}」", danger = true),
                CandItem("取消", accent = true),
            )
            BarKind.CANDIDATES -> st!!.candidates.take(MAX_BAR).mapIndexed { i, c ->
                CandItem(display(c.text), highlight = i == 0, badge = when (c.source) {
                    CandidateSource.USER -> "U"; CandidateSource.CONTACT -> "C"; else -> null
                })
            }
            BarKind.WORD -> {
                val w = wordBuf.toString()
                wordMatches = store.words().filter { it.length > w.length && it.startsWith(w, ignoreCase = true) }.take(20)
                val inDict = store.words().any { it == w }
                listOf(CandItem(if (inDict) "$w ✓" else "$w ＋", accent = true)) + wordMatches.map { CandItem(it) }
            }
            BarKind.ASSOC -> assoc.map { CandItem(display(it.text)) }
            BarKind.NONE -> emptyList()
        }
        val chip = clipChip()
        val showCands = shownItems.isNotEmpty() || chip != null
        candBar.visibility = if (showCands) View.VISIBLE else View.GONE
        toolbar.visibility = if (showCands) View.GONE else View.VISIBLE
        candBar.setChip(chip)
        candBar.setItems(shownItems)
        if (panels.overlay == Overlay.CANDIDATES) {
            if (barKind == BarKind.CANDIDATES) candGrid.setItems(allItems()) else showOverlay(Overlay.NONE)
        }
        syncToolbarActive()
    }

    /** 剛複製的內容 → 候選列「一點即貼」（2 分鐘內、尚未打字時）。 */
    private fun clipChip(): SpecialChip? {
        if (!prefs.clipChip || searching || panels.mode != PanelMode.KEYBOARD) return null
        if (barKind != BarKind.NONE) return null
        val r = clips.recent ?: return null
        if (System.currentTimeMillis() - r.time > 120_000) return null
        return SpecialChip("📋 " + r.text.replace('\n', ' ').take(24))
    }

    private fun allItems(): List<CandItem> {
        val st = engine?.state ?: return shownItems
        return if (st.isComposing) st.candidates.mapIndexed { i, c -> CandItem(display(c.text), highlight = i == 0) } else shownItems
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
        toolbar.setActive(ToolButton.SETTINGS, panels.overlay in setOf(Overlay.GEAR, Overlay.HEIGHT, Overlay.PHRASES))
        toolbar.setActive(ToolButton.EDIT, panels.overlay == Overlay.EDIT)
        toolbar.setActive(ToolButton.CLIPBOARD, panels.mode == PanelMode.CLIPBOARD)
        toolbar.setActive(ToolButton.MIC, panels.mode == PanelMode.VOICE)
    }

    // =====================================================================
    // 送字
    // =====================================================================

    /** 中文輸出：簡體模式時轉成簡體（需求八）。 */
    private fun display(text: String) = if (prefs.simplified) ChineseConverter.toSimplified(text) else text

    private fun commit(text: String) {
        currentTarget.commitText(display(text))
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
            e.select(0).commit?.let { currentTarget.commitText(display(it)) }
        }
        assoc = emptyList()
    }

    private fun pick(i: Int) {
        val e = engine
        when (barKind) {
            BarKind.DELETE -> {
                val c = pendingDelete
                pendingDelete = null
                if (i == 0 && c != null && e != null) {
                    if (e.deleteUserPhrase(c)) toast("已從字典刪除「${c.text}」")
                }
            }
            BarKind.CANDIDATES -> e?.select(i)?.commit?.let { commit(it) }
            BarKind.ASSOC -> assoc.getOrNull(i)?.let { commit(it.text) }
            BarKind.WORD -> {
                val w = wordBuf.toString()
                if (i == 0) {
                    if (store.addWord(w)) toast("已加入字典：$w") else toast("「$w」已在字典中")
                } else {
                    val m = wordMatches.getOrNull(i - 1) ?: return
                    currentTarget.deleteSurroundingText(w.length, 0)
                    currentTarget.commitText(m)
                    wordBuf.setLength(0)
                }
            }
            BarKind.NONE -> {}
        }
        render()
    }

    /** 長按使用者詞 → 先詢問再刪除（需求六）。 */
    private fun longPick(i: Int) {
        if (barKind != BarKind.CANDIDATES) return
        val c = engine?.state?.candidates?.getOrNull(i) ?: return
        if (c.source == CandidateSource.SYSTEM) {
            toast("「${c.text}」是內建詞，無法刪除")
            return
        }
        pendingDelete = c
        render()
    }

    // =====================================================================
    // 鍵盤事件
    // =====================================================================

    override fun onKey(key: KeyDef) {
        val e = engine
        pendingDelete = null
        clips.dismissRecent()
        when (val a = key.action) {
            is KeyAction.Zhuyin -> {
                if (e == null) { notifyLoading(); return }
                assoc = emptyList()
                wordBuf.setLength(0)
                e.onSymbol(a.c)
            }
            is KeyAction.Commit -> {
                if (e?.state?.isComposing == true) commitComposing()
                currentTarget.commitText(a.text)
                assoc = emptyList()
                trackWord(a.text)
                if (layout == Layout.SYMBOL && !symbolSticky) { layout = Layout.ZHUYIN; applyLayout() }
                if (layout == Layout.ENGLISH && shiftState == 1) { shiftState = 0; applyLayout() }
            }
            KeyAction.Space -> {
                if (e?.state?.isComposing == true) e.onSymbol(' ')
                else { assoc = emptyList(); wordBuf.setLength(0); currentTarget.commitText(" ") }
            }
            KeyAction.Backspace -> {
                if (e?.state?.isComposing == true) e.onBackspace()
                else {
                    assoc = emptyList()
                    if (wordBuf.isNotEmpty()) wordBuf.setLength(wordBuf.length - 1)
                    currentTarget.deleteSurroundingText(1, 0)
                }
            }
            KeyAction.Enter -> {
                if (e?.state?.isComposing == true) commitComposing()
                else {
                    assoc = emptyList()
                    wordBuf.setLength(0)
                    when {
                        searching -> clips.search(searchTarget.query).firstOrNull()?.let {
                            exitSearch(backToPanel = false); pasteClip(it.text)
                        }
                        currentTarget === imeTarget -> imeTarget.enter()
                        else -> currentTarget.commitText("\n")
                    }
                }
            }
            KeyAction.Shift -> onShift()
            KeyAction.ToEnglish -> { commitComposing(); wordBuf.setLength(0); layout = Layout.ENGLISH; shiftState = 0; applyLayout() }
            KeyAction.ToZhuyin -> { layout = Layout.ZHUYIN; pinned = false; wordBuf.setLength(0); applyLayout() }
            KeyAction.Pin -> { pinned = !pinned; applyLayout() }
            KeyAction.Fan, KeyAction.Punct -> {} // 由手勢回呼處理
        }
        render()
    }

    /** 大寫鍵：點一下 = 下一個字母大寫；快速點兩下 = 大寫鎖定；鎖定中再點一下 = 解除（需求二）。 */
    private fun onShift() {
        val now = SystemClock.uptimeMillis()
        shiftState = when {
            shiftState == 2 -> 0
            shiftState == 1 && now - lastShiftTap < DOUBLE_TAP_MS -> 2
            shiftState == 0 -> 1
            else -> 0
        }
        lastShiftTap = now
        applyLayout()
    }

    /** 英文／半形符號／數字連續輸入時累積起來，候選列可「＋加入字典」（需求七）。 */
    private fun trackWord(text: String) {
        val ok = text.length == 1 && text[0].code in 0x21..0x7E && layout != Layout.ZHUYIN && !currentTarget.hints.isPassword
        if (ok) wordBuf.append(text) else wordBuf.setLength(0)
        if (wordBuf.length > 64) wordBuf.setLength(0)
    }

    override fun onPunct(text: String) {
        commitComposing()
        currentTarget.commitText(text)
        assoc = emptyList()
        wordBuf.setLength(0)
        pendingDelete = null
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
            openClipboard()
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
    // 工具列、齒輪選單、高度、常用短語
    // =====================================================================

    private fun onToolbar(b: ToolButton) {
        when (b) {
            ToolButton.SETTINGS -> toggleOverlay(Overlay.GEAR)
            ToolButton.MIC -> toast("語音模組尚未合併")
            ToolButton.TRAD_SIMP -> {
                prefs.simplified = !prefs.simplified
                toolbar.simplified = prefs.simplified
                toast(if (prefs.simplified) "已切換為簡體輸出" else "已切換為繁體輸出")
                render()
            }
            ToolButton.LEFT -> { commitComposing(); wordBuf.setLength(0); editor.selecting = false; editor.left() }
            ToolButton.RIGHT -> { commitComposing(); wordBuf.setLength(0); editor.selecting = false; editor.right() }
            ToolButton.CLIPBOARD -> if (panels.mode == PanelMode.CLIPBOARD) closeClipboard() else openClipboard()
            ToolButton.EDIT -> if (searching) toast("搜尋剪貼簿時無法使用編輯面板") else toggleOverlay(Overlay.EDIT)
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
            "phrases" -> showQuickPhrases()
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

    /** 常用短語（需求十一）：點一下送出；「管理」開設定頁新增、編輯。 */
    private fun showQuickPhrases() {
        phrasePanel.setHeader("常用短語", listOf(
            RowListView.Action("↩ 返回") { showOverlay(Overlay.NONE) },
            RowListView.Action("管理") { showOverlay(Overlay.NONE); openDictionary(DictionaryActivity.MODE_QUICK) },
        ))
        phrasePanel.setRows(store.quickPhrases().map { q ->
            RowListView.Row(text = q, onClick = {
                showOverlay(Overlay.NONE)
                commitComposing()
                currentTarget.commitText(q)
                render()
            })
        }, "還沒有常用短語。\n按上方「管理」新增，例如地址、Email、常用回覆。")
        showOverlay(Overlay.PHRASES)
    }

    private fun openDictionary(mode: String) {
        ctx.startActivity(Intent(ctx, DictionaryActivity::class.java)
            .putExtra(DictionaryActivity.EXTRA_MODE, mode)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // =====================================================================
    // 編輯面板
    // =====================================================================

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
            // 需求九：有選取時改為「納入字典」
            if (sel) Tile("addDict", "納入字典", "＋") else Tile("ai2", "AI 寫作", "✦", true),
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
            "addDict" -> addSelectionToDictionary()
            "ai1", "ai2", "ai3" -> toast("AI 改寫模組尚未合併（REQUESTS R1）")
        }
        refreshEditTiles()
    }

    /** 長按「貼上」→ 剪貼簿面板。 */
    private fun onEditLong(t: Tile) {
        if (t.id == "paste") openClipboard()
    }

    /**
     * 納入字典（需求九）：中文自動反查注音；英文／符號加入英文詞。
     * 反查不到時請使用者到設定頁的字典手動新增。
     */
    private fun addSelectionToDictionary() {
        val text = imeTarget.selectedText()?.toString()?.trim()
        if (text.isNullOrEmpty()) { toast("請先選取文字"); return }
        if (text.length > 20) { toast("選取的文字太長（最多 20 字）"); return }
        val hasCjk = text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        if (!hasCjk) {
            if (store.addWord(text)) toast("已加入英文詞：$text") else toast("「$text」已在字典中")
            return
        }
        loader.execute {
            val r = LexiconHolder.peek()?.readingsFor(text)
            main.post {
                if (r == null) {
                    toast("找不到「$text」的注音，請到 設定 → 字典 手動新增")
                } else {
                    store.addPhrase(text, r)
                    engine?.addUserPhrase(text, r, CandidateSource.USER)
                    toast("已納入字典：$text（${r.joinToString(" ")}）")
                }
            }
        }
    }

    // =====================================================================
    // 剪貼簿（需求十三）
    // =====================================================================

    /** Android 13+ 密碼管理器等會把內容標為敏感，這類內容不記錄。 */
    private fun isSensitiveClip(): Boolean = try {
        val d = cm.primaryClipDescription
        d?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
    } catch (e: Exception) {
        false
    }

    private fun openClipboard() {
        commitComposing()
        wordBuf.setLength(0)
        confirmClear = false
        refreshClipPanel()
        panels.showPanel(PanelMode.CLIPBOARD)
        render()
    }

    private fun closeClipboard() {
        panels.showPanel(PanelMode.KEYBOARD)
        render()
    }

    private fun refreshClipPanel() {
        val all = clips.all()
        val title = "剪貼簿 ${all.size} 筆" + if (!prefs.clipCapture) "（記錄已關閉）" else ""
        clipPanel.setHeader(title, listOf(
            RowListView.Action("↩ 鍵盤") { closeClipboard() },
            RowListView.Action("🔍 搜尋") { enterSearch() },
            RowListView.Action(if (confirmClear) "確定清除？" else "清除", highlight = confirmClear) {
                if (confirmClear) {
                    clips.clearUnpinned()
                    confirmClear = false
                    toast("已清除未釘選的項目")
                } else {
                    confirmClear = true
                    refreshClipPanel()
                }
            },
        ))
        val now = System.currentTimeMillis()
        clipPanel.setRows(all.map { c ->
            RowListView.Row(
                text = c.text,
                sub = ago(now - c.time) + if (c.pinned) "・已釘選" else "",
                pinned = c.pinned,
                onClick = { pasteClip(c.text) },
                actions = listOf(
                    RowListView.Action(if (c.pinned) "📍" else "📌", highlight = c.pinned) { clips.togglePin(c.text) },
                    RowListView.Action("✕") { clips.remove(c.text) },
                ),
            )
        }, "還沒有剪貼簿紀錄。\n在任何 App 複製文字後，這裡就會出現（釘選的項目不會被清除）。")
    }

    private fun pasteClip(text: String) {
        imeTarget.commitText(text)
        clips.dismissRecent()
        if (panels.mode == PanelMode.CLIPBOARD) panels.showPanel(PanelMode.KEYBOARD)
        render()
    }

    private fun enterSearch() {
        searching = true
        searchTarget.clear()
        engine?.reset()
        currentTarget = searchTarget
        panels.showPanel(PanelMode.KEYBOARD)
        layout = Layout.ZHUYIN
        applyLayout()
        searchStrip.show("", clips.all())
        searchSlot.visibility = View.VISIBLE
        render()
    }

    private fun onSearchChange(q: String) {
        if (viewReady && searching) searchStrip.show(q, clips.search(q))
    }

    private fun exitSearch(backToPanel: Boolean) {
        searching = false
        engine?.reset()
        currentTarget = imeTarget
        searchSlot.visibility = View.GONE
        if (backToPanel) openClipboard() else render()
    }

    private fun ago(ms: Long): String = when {
        ms < 60_000 -> "剛剛"
        ms < 3_600_000 -> "${ms / 60_000} 分鐘前"
        ms < 86_400_000 -> "${ms / 3_600_000} 小時前"
        else -> "${ms / 86_400_000} 天前"
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
        private const val DOUBLE_TAP_MS = 350L
    }
}
