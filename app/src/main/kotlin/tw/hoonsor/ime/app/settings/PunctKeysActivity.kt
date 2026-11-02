package tw.hoonsor.ime.app.settings

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import tw.hoonsor.ime.app.keyboard.KeyAction
import tw.hoonsor.ime.app.keyboard.PunctSlot
import tw.hoonsor.ime.app.ui.Palette
import tw.hoonsor.ime.app.ui.dpi

/**
 * 自訂四符號鍵：每顆鍵四個欄位，排法與鍵面四角相同。改完立刻儲存，下次切換輸入框或鍵盤就生效。
 * 欄位留空＝該角用預設值。只用 Android 框架元件。
 */
class PunctKeysActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dpi(20f)
            setPadding(p, p, p, p)
        }
        col.addView(TextView(this).apply {
            text = "自訂四符號鍵"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setPadding(0, dpi(4f), 0, dpi(4f))
        })
        col.addView(TextView(this).apply {
            text = "欄位位置和鍵面四角一樣。粉紅色的左下角是點一下輸出的內容，其他三角要往該方向滑。\n" +
                "・每格最多 ${PunctSlot.MAX_CHARS} 個字，可以填多個字（例如 .com）。\n" +
                "・留空＝使用預設值。\n" +
                "・空白鍵點一下固定是空白（注音的一聲要用），只能改其他三角。\n" +
                "・改完自動儲存，切換輸入框後鍵盤就會更新。"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        })
        PunctSlot.entries.forEach { addSlot(col, it) }

        val scroll = ScrollView(this).apply {
            addView(col)
            isFillViewport = true
            // Android 15+ 強制全螢幕：上方狀態列、下方手勢列的空間要自己留，否則內容會被蓋住
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = if (android.os.Build.VERSION.SDK_INT >= 30)
                    insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
                else null
                @Suppress("DEPRECATION")
                v.setPadding(0, bars?.top ?: insets.systemWindowInsetTop, 0, bars?.bottom ?: insets.systemWindowInsetBottom)
                insets
            }
        }
        setContentView(scroll)
    }

    private fun addSlot(col: LinearLayout, slot: PunctSlot) {
        col.addView(TextView(this).apply {
            text = slot.title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, dpi(18f), 0, dpi(6f))
        })
        val cur = prefs.punct(slot)
        val def = slot.default
        val upLeft = field(def.upLeft, cur.upLeft)
        val upRight = field(def.upRight, cur.upRight)
        val downLeft = if (slot.tapLocked) null else field(def.downLeft, cur.downLeft, pink = true)
        val downRight = field(def.downRight, cur.downRight)

        col.addView(row(cell("↖ 左上", upLeft), cell("↗ 右上", upRight)))
        col.addView(row(cell("點一下（左下）", downLeft ?: lockedSpace(), pink = true), cell("↘ 右下", downRight)))

        val all = listOfNotNull(downLeft, upLeft, upRight, downRight)
        // 欄位空白時存空字串，由 PunctSlot.decode 換成預設值
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = prefs.setPunct(slot, KeyAction.Punct(
                downLeft = downLeft?.text?.toString() ?: def.downLeft,
                upLeft = upLeft.text.toString(),
                upRight = upRight.text.toString(),
                downRight = downRight.text.toString(),
            ))
        }
        all.forEach { it.addTextChangedListener(watcher) }

        col.addView(Button(this).apply {
            text = "恢復預設"
            setOnClickListener {
                all.forEach { it.removeTextChangedListener(watcher) }
                downLeft?.setText(def.downLeft)
                upLeft.setText(def.upLeft)
                upRight.setText(def.upRight)
                downRight.setText(def.downRight)
                all.forEach { it.addTextChangedListener(watcher) }
                prefs.resetPunct(slot)
            }
        })
    }

    private fun field(hintText: String, value: String, pink: Boolean = false) = EditText(this).apply {
        hint = hintText
        setText(value)
        isSingleLine = true
        gravity = Gravity.CENTER
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        filters = arrayOf(InputFilter.LengthFilter(PunctSlot.MAX_CHARS))
        if (pink) setTextColor(Palette.PINK)
    }

    private fun lockedSpace() = TextView(this).apply {
        text = "空白（固定）"
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(0, dpi(10f), 0, dpi(10f))
    }

    private fun row(left: View, right: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left)
        addView(right)
    }

    /** 上面一行標籤、下面一個欄位。 */
    private fun cell(label: String, v: View, pink: Boolean = false) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dpi(8f)
        }
        addView(TextView(this@PunctKeysActivity).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            if (pink) setTextColor(Palette.PINK)
        })
        addView(v)
    }
}
