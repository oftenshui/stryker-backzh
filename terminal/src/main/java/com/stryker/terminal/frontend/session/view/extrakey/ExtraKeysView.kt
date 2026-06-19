package com.stryker.terminal.frontend.session.view.extrakey

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.*
import android.widget.GridLayout
import android.widget.LinearLayout
import com.stryker.terminal.R
import com.stryker.terminal.component.ComponentManager
import com.stryker.terminal.component.config.NeoPreference
import com.stryker.terminal.component.config.NeoTermPath
import com.stryker.terminal.component.extrakey.ExtraKeyComponent
import com.stryker.terminal.frontend.session.terminal.ToggleImeEvent
import org.greenrobot.eventbus.EventBus
import java.io.File

class ExtraKeysView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
  companion object {
    private val ESC = ControlButton(IExtraButton.KEY_ESC)
    private val TAB = ControlButton(IExtraButton.KEY_TAB)
    private val PAGE_UP = RepeatableButton(IExtraButton.KEY_PAGE_UP)
    private val PAGE_DOWN = RepeatableButton(IExtraButton.KEY_PAGE_DOWN)
    private val HOME = ControlButton(IExtraButton.KEY_HOME)
    private val END = ControlButton(IExtraButton.KEY_END)
    private val ARROW_UP = ArrowButton(IExtraButton.KEY_ARROW_UP)
    private val ARROW_DOWN = ArrowButton(IExtraButton.KEY_ARROW_DOWN)
    private val ARROW_LEFT = ArrowButton(IExtraButton.KEY_ARROW_LEFT)
    private val ARROW_RIGHT = ArrowButton(IExtraButton.KEY_ARROW_RIGHT)
    private val SLASH = ControlButton(IExtraButton.KEY_SLASH)
    private val TOGGLE_IME = object : ControlButton(KEY_TOGGLE_IME) {
      override fun onClick(view: View) {
        EventBus.getDefault().post(ToggleImeEvent())
      }
    }

    private val MAX_BUTTONS_PER_LINE = 7
    private val DEFAULT_ALPHA = 0.8f
    private val EXPANDED_ALPHA = 0.5f
    private val USER_KEYS_BUTTON_LINE_START = 2
  }

  private val builtinKeys = mutableListOf<IExtraButton>()
  private val userKeys = mutableListOf<IExtraButton>()

  private val buttonBars: MutableList<LinearLayout> = mutableListOf()
  private var typeface: Typeface? = null

  private val CTRL = StatedControlButton(IExtraButton.KEY_CTRL)
  private val ALT = StatedControlButton(IExtraButton.KEY_ALT)
  private val FN = StatedControlButton(IExtraButton.KEY_FN)
  private val SHIFT = StatedControlButton(IExtraButton.KEY_SHIFT)

  private var buttonPanelExpanded = false
  private val EXPAND_BUTTONS = object : ControlButton(IExtraButton.KEY_SHOW_ALL_BUTTONS) {
    override fun onClick(view: View) {
      expandButtonPanel()
    }
  }

  private val extraKeyComponent: ExtraKeyComponent

  init {
    alpha = DEFAULT_ALPHA
    gravity = Gravity.TOP
    orientation = VERTICAL
    typeface = Typeface.createFromAsset(context.assets, "eks_font.ttf")
    extraKeyComponent = ComponentManager.getComponent<ExtraKeyComponent>()

    initBuiltinKeys()
    loadDefaultUserKeys()
    updateButtons()
    expandButtonPanel(false)
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_DOWN) {
      if (buttonPanelExpanded) {
        expandButtonPanel()
        return true
      }
      return false
    }
    return super.onKeyDown(keyCode, event)
  }

  fun setTextColor(textColor: Int) {
    IExtraButton.NORMAL_TEXT_COLOR = textColor
    updateButtons()
  }

  fun setTypeface(typeface: Typeface?) {
    this.typeface = typeface
    updateButtons()
  }

  fun readControlButton(): Boolean {
    return CTRL.readState()
  }

  fun readAltButton(): Boolean {
    return ALT.readState()
  }

  fun readFnButton(): Boolean {
    return FN.readState()
  }

  fun readShiftButton(): Boolean {
    return SHIFT.readState()
  }

  fun addUserKey(button: IExtraButton) {
    addKeyButton(userKeys, button)
  }

  fun addBuiltinKey(button: IExtraButton) {
    addKeyButton(builtinKeys, button)
  }

  fun clearUserKeys() {
    userKeys.clear()
  }

  fun loadDefaultUserKeys() {
    clearUserKeys()
    val defaultConfig = extraKeyComponent.loadConfigure(File(NeoTermPath.EKS_DEFAULT_FILE))
    if (defaultConfig != null) {
      userKeys.addAll(defaultConfig.shortcutKeys)
    }
  }

  fun updateButtons() {
    buttonBars.forEach { it.removeAllViews() }

    var targetButtonBarIndex = 0
    builtinKeys.plus(userKeys).forEachIndexed { index, button ->
      addKeyButton(getButtonBarOrNew(targetButtonBarIndex), button)
      targetButtonBarIndex = (index + 1) / MAX_BUTTONS_PER_LINE
    }
    updateButtonBars()
  }

  private fun updateButtonBars() {
    removeAllViews()

    buttonBars.asReversed()
      .forEach { addView(it) }
  }

  private fun expandButtonPanel(forceSetExpanded: Boolean? = null) {
    if (buttonBars.size <= 2) {
      return
    }

    buttonPanelExpanded = forceSetExpanded ?: !buttonPanelExpanded
    val visibility = if (buttonPanelExpanded) View.VISIBLE else View.GONE
    alpha = if (buttonPanelExpanded) EXPANDED_ALPHA else DEFAULT_ALPHA

    IntRange(USER_KEYS_BUTTON_LINE_START, buttonBars.size - 1)
      .map { buttonBars[it] }
      .forEach { it.visibility = visibility }
  }

  private fun createNewButtonBar(): LinearLayout {
    val line = LinearLayout(context)

    val layoutParams =
      if (NeoPreference.isExplicitExtraKeysWeightEnabled())
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
      else
        LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        )

    layoutParams.setMargins(0, 0, 0, 0)
    line.setPadding(0, 0, 0, 0)
    line.gravity = Gravity.START
    line.orientation = LinearLayout.HORIZONTAL
    line.layoutParams = layoutParams
    return line
  }

  private fun getButtonBarOrNew(position: Int): LinearLayout {
    if (position >= buttonBars.size) {
      for (i in 0..(position - buttonBars.size + 1)) {
        buttonBars.add(createNewButtonBar())
      }
    }
    return buttonBars[position]
  }

  private fun addKeyButton(buttons: MutableList<IExtraButton>?, button: IExtraButton) {
    if (buttons != null && !buttons.contains(button)) {
      buttons.add(button)
    }
  }

  private fun addKeyButton(contentView: LinearLayout, extraButton: IExtraButton) {
    val outerButton = extraButton.makeButton(context, null, android.R.attr.buttonBarButtonStyle)

    val param = GridLayout.LayoutParams()
    param.setGravity(Gravity.CENTER)
    param.width = calculateButtonWidth()
    param.height = context.resources.getDimensionPixelSize(R.dimen.eks_height)
    param.setMargins(0, 0, 0, 0)

    outerButton.layoutParams = param
    outerButton.maxLines = 1
    outerButton.typeface = typeface
    outerButton.text = extraButton.displayText
    outerButton.setPadding(0, 0, 0, 0)
    outerButton.setTextColor(IExtraButton.NORMAL_TEXT_COLOR)
    outerButton.isAllCaps = false

    outerButton.setOnClickListener {
      val root = rootView
      extraButton.onClick(root)
    }
    contentView.addView(outerButton)
  }

  private fun initBuiltinKeys() {
    addBuiltinKey(TAB)
    addBuiltinKey(CTRL)

    addBuiltinKey(PAGE_DOWN)
    addBuiltinKey(ARROW_LEFT)
    addBuiltinKey(ARROW_DOWN)
    addBuiltinKey(ARROW_RIGHT)
    addBuiltinKey(TOGGLE_IME)

    addBuiltinKey(ESC)
    addBuiltinKey(ALT)
    addBuiltinKey(PAGE_UP)
    addBuiltinKey(HOME)
    addBuiltinKey(ARROW_UP)
    addBuiltinKey(END)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      addBuiltinKey(EXPAND_BUTTONS)
    } else {
      addBuiltinKey(SLASH)
    }

  }

  private fun calculateButtonWidth(): Int {
    return context.resources.displayMetrics.widthPixels / ExtraKeysView.MAX_BUTTONS_PER_LINE
  }
}
