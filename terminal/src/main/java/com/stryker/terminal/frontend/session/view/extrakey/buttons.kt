package com.stryker.terminal.frontend.session.view.extrakey

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ToggleButton
import androidx.appcompat.widget.AppCompatButton
import com.stryker.terminal.R
import com.stryker.terminal.frontend.session.view.TerminalView

open class ControlButton(text: String) : TextButton(text, false)

abstract class IExtraButton : View.OnClickListener {

  var buttonKeys: CombinedSequence? = null
  var displayText: String? = null

  override fun toString(): String {
    return "${this.javaClass.simpleName} { display: $displayText, code: ${buttonKeys?.keys} }"
  }

  abstract override fun onClick(view: View)

  abstract fun makeButton(context: Context?, attrs: AttributeSet?, defStyleAttr: Int): Button

  companion object {
    const val KEY_ESC = "Esc"
    const val KEY_TAB = "Tab"
    const val KEY_CTRL = "Ctrl"
    const val KEY_ALT = "Alt"
    const val KEY_PAGE_UP = "PgUp"
    const val KEY_PAGE_DOWN = "PgDn"
    const val KEY_HOME = "Home"
    const val KEY_END = "End"
    const val KEY_ARROW_UP_TEXT = "Up"
    const val KEY_ARROW_DOWN_TEXT = "Down"
    const val KEY_ARROW_LEFT_TEXT = "Left"
    const val KEY_ARROW_RIGHT_TEXT = "Right"
    const val KEY_SHOW_ALL_BUTTONS = "···"
    const val KEY_SLASH = "/"
    const val KEY_TOGGLE_IME = "⌨"

    const val KEY_ARROW_UP = "▲"
    const val KEY_ARROW_DOWN = "▼"
    const val KEY_ARROW_LEFT = "◀"
    const val KEY_ARROW_RIGHT = "▶"

    const val KEY_FN = "Fn"
    const val KEY_F1 = "F1"
    const val KEY_F2 = "F2"
    const val KEY_F3 = "F3"
    const val KEY_F4 = "F4"
    const val KEY_F5 = "F5"
    const val KEY_F6 = "F6"
    const val KEY_F7 = "F7"
    const val KEY_F8 = "F8"
    const val KEY_F9 = "F9"
    const val KEY_F10 = "F10"
    const val KEY_F11 = "F11"
    const val KEY_F12 = "F12"

    const val KEY_SHIFT = "LShift"
    const val KEY_DEL   = "Del"
    const val KEY_ENTER = "Enter"

    var NORMAL_TEXT_COLOR = 0xFFFFFFFF.toInt()
    var SELECTED_TEXT_COLOR = 0xFF80DEEA.toInt()

    fun sendKey(view: View, keyName: String) {
      var keyCode = 0
      var chars = ""
      when (keyName) {
        KEY_ESC -> keyCode = KeyEvent.KEYCODE_ESCAPE
        KEY_TAB -> keyCode = KeyEvent.KEYCODE_TAB
        KEY_ARROW_UP -> keyCode = KeyEvent.KEYCODE_DPAD_UP
        KEY_ARROW_LEFT -> keyCode = KeyEvent.KEYCODE_DPAD_LEFT
        KEY_ARROW_RIGHT -> keyCode = KeyEvent.KEYCODE_DPAD_RIGHT
        KEY_ARROW_DOWN -> keyCode = KeyEvent.KEYCODE_DPAD_DOWN
        KEY_ARROW_UP_TEXT -> keyCode = KeyEvent.KEYCODE_DPAD_UP
        KEY_ARROW_LEFT_TEXT -> keyCode = KeyEvent.KEYCODE_DPAD_LEFT
        KEY_ARROW_RIGHT_TEXT -> keyCode = KeyEvent.KEYCODE_DPAD_RIGHT
        KEY_ARROW_DOWN_TEXT -> keyCode = KeyEvent.KEYCODE_DPAD_DOWN
        KEY_PAGE_UP -> keyCode = KeyEvent.KEYCODE_PAGE_UP
        KEY_PAGE_DOWN -> keyCode = KeyEvent.KEYCODE_PAGE_DOWN
        KEY_HOME -> keyCode = KeyEvent.KEYCODE_MOVE_HOME
        KEY_END -> keyCode = KeyEvent.KEYCODE_MOVE_END
        KEY_FN -> keyCode = KeyEvent.KEYCODE_FUNCTION
        KEY_SHIFT -> keyCode = KeyEvent.KEYCODE_SHIFT_LEFT
        KEY_SLASH -> keyCode = KeyEvent.KEYCODE_SLASH
        "―" -> chars = "-"

        KEY_FN -> keyCode = KeyEvent.KEYCODE_FUNCTION
        KEY_F1 -> keyCode = KeyEvent.KEYCODE_F1
        KEY_F2 -> keyCode = KeyEvent.KEYCODE_F2
        KEY_F3 -> keyCode = KeyEvent.KEYCODE_F3
        KEY_F4 -> keyCode = KeyEvent.KEYCODE_F4
        KEY_F5 -> keyCode = KeyEvent.KEYCODE_F5
        KEY_F6 -> keyCode = KeyEvent.KEYCODE_F6
        KEY_F7 -> keyCode = KeyEvent.KEYCODE_F7
        KEY_F8 -> keyCode = KeyEvent.KEYCODE_F8
        KEY_F9 -> keyCode = KeyEvent.KEYCODE_F9
        KEY_F10 -> keyCode = KeyEvent.KEYCODE_F10
        KEY_F11 -> keyCode = KeyEvent.KEYCODE_F11
        KEY_F12 -> keyCode = KeyEvent.KEYCODE_F12

        KEY_DEL -> keyCode = KeyEvent.KEYCODE_DEL
        KEY_ENTER -> keyCode = KeyEvent.KEYCODE_ENTER

        else -> chars = keyName
      }

      if (keyCode > 0) {
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
      } else if (chars.isNotEmpty()) {
        val terminalView = view.findViewById<TerminalView>(R.id.terminal_view)
        val session = terminalView.currentSession
        session?.write(chars)
      }
    }
  }
}

open class RepeatableButton(buttonText: String) : ControlButton(buttonText) {

  override fun makeButton(context: Context?, attrs: AttributeSet?, defStyleAttr: Int): Button {
    return RepeatableButtonWidget(context, attrs, defStyleAttr)
  }

  private class RepeatableButtonWidget(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) :
    AppCompatButton(context!!, attrs, defStyleAttr) {

    private val LONG_CLICK_ACTION_INTERVAL = 100L

    private var isMotionEventUp = true

    var mHandler: Handler = object : Handler(Looper.getMainLooper()) {
      override fun handleMessage(msg: android.os.Message) {
        if (!isMotionEventUp && isEnabled) {
          performClick()
          this.sendEmptyMessageDelayed(0, LONG_CLICK_ACTION_INTERVAL)
        }
      }
    }

    init {
      this.setOnLongClickListener {
        isMotionEventUp = false
        mHandler.sendEmptyMessage(0)
        false
      }
      this.setOnTouchListener { _, event ->
        if (event.action == MotionEvent.ACTION_UP) {
          isMotionEventUp = true
        }
        false
      }
    }

    override fun performClick(): Boolean {
      return super.performClick()
    }
  }
}

open class StatedControlButton @JvmOverloads constructor(text: String, var initState: Boolean = false) :
  ControlButton(text) {
  var toggleButton: ToggleButton? = null

  override fun onClick(view: View) {
    setStatus(toggleButton?.isChecked)
  }

  override fun makeButton(context: Context?, attrs: AttributeSet?, defStyleAttr: Int): Button {
    val outerButton = ToggleButton(context, null, android.R.attr.buttonBarButtonStyle)

    outerButton.isClickable = true
    if (initState) {
      outerButton.isChecked = true
      outerButton.setTextColor(SELECTED_TEXT_COLOR)
    }

    this.toggleButton = outerButton
    return outerButton
  }

  private fun setStatus(status: Boolean?) {
    val button = toggleButton
    if (button != null && status != null) {
      button.isChecked = status
      button.setTextColor(
        if (status) SELECTED_TEXT_COLOR
        else NORMAL_TEXT_COLOR
      )
    }
  }

  fun readState(): Boolean {
    val button = toggleButton ?: return false

    if (button.isPressed) return true
    val result = button.isChecked
    if (result) {
      button.isChecked = false
      button.setTextColor(NORMAL_TEXT_COLOR)
    }
    return result
  }
}

open class TextButton constructor(text: String, val withEnter: Boolean = false) : IExtraButton() {
  init {
    this.buttonKeys = CombinedSequence.solveString(text)
    this.displayText = text
  }

  override fun onClick(view: View) {
    buttonKeys!!.keys.forEach {
      sendKey(view, it)
    }
    if (withEnter) {
      sendKey(view, "\n")
    }
  }

  override fun makeButton(context: Context?, attrs: AttributeSet?, defStyleAttr: Int): Button {
    return Button(context, attrs, defStyleAttr)
  }
}

class ArrowButton(arrowText: String) : RepeatableButton(arrowText)
