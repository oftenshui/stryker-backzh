package com.stryker.terminal.frontend.session.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.media.AudioManager
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat.getSystemService
import com.stryker.terminal.BuildConfig
import com.stryker.terminal.R
import com.stryker.terminal.backend.KeyHandler
import com.stryker.terminal.backend.TerminalSession
import com.stryker.terminal.component.ComponentManager
import com.stryker.terminal.component.completion.*
import com.stryker.terminal.component.config.NeoPreference
import com.stryker.terminal.component.extrakey.ExtraKeyComponent
import com.stryker.terminal.component.session.ShellTermSession
import com.stryker.terminal.frontend.completion.CandidatePopupWindow
import com.stryker.terminal.frontend.session.view.TerminalView
import com.stryker.terminal.frontend.session.view.TerminalViewClient
import java.util.*

class TermViewClient(val context: Context) :
    TerminalViewClient {
  private var mVirtualControlKeyDown: Boolean = false
  private var mVirtualFnKeyDown: Boolean = false
  private var mVirtualShiftKeyDown: Boolean = false
  private var lastTitle: String = ""

  var termSessionData: TermSessionData? = null

  override fun onScale(scale: Float): Float {
    if (scale < 0.9f || scale > 1.1f) {
      val increase = scale > 1f
      changeFontSize(increase)
      return 1.0f
    }
    return scale
  }

  override fun onSingleTapUp(e: MotionEvent?) {
    val termView = termSessionData?.termView ?: return
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
      .showSoftInput(termView, InputMethodManager.SHOW_IMPLICIT)
  }

  override fun shouldBackButtonBeMappedToEscape(): Boolean {
    val shellSession = termSessionData?.termSession as ShellTermSession? ?: return false
    return shellSession.shellProfile.enableBackKeyToEscape
  }

  override fun copyModeChanged(copyMode: Boolean) {
  }

  override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
    if (handleVirtualKeys(keyCode, e, true)) {
      return true
    }

    val termUI = termSessionData?.termUI

    when (keyCode) {
      KeyEvent.KEYCODE_ENTER -> {
        if (e?.action == KeyEvent.ACTION_DOWN && session?.isRunning == false) {
          termUI?.requireClose()
          return true
        }
        return false
      }
      KeyEvent.KEYCODE_BACK -> {
        if (e?.action == KeyEvent.ACTION_DOWN) {
          return termUI?.requireFinishAutoCompletion() ?: false
        }
        return false
      }
    }

    if (e != null && e.isCtrlPressed && e.isShiftPressed) {
      val unicodeChar = e.getUnicodeChar(0).toChar()

      when (unicodeChar) {
        'v' -> termUI?.requirePaste()
        'n' -> termUI?.requireCreateNew()
        'z' -> termUI?.requireSwitchToPrevious()
        'x' -> termUI?.requireSwitchToNext()
        'f' -> termUI?.requireToggleFullScreen()
        '-' -> changeFontSize(false)
        '+' -> changeFontSize(true)
      }

      return true
    } else if (e != null && e.isAltPressed) {
      val unicodeChar = e.getUnicodeChar(0).toChar()
      if (unicodeChar !in ('1'..'9')) {
        return false
      }

      val sessionIndex = unicodeChar.toInt() - '0'.toInt()
      termUI?.requireSwitchTo(sessionIndex)

      return true
    }
    return false
  }

  override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean {
    return handleVirtualKeys(keyCode, e, false)
  }

  override fun readControlKey(): Boolean {
    val extraKeysView = termSessionData?.extraKeysView
    return (extraKeysView != null && extraKeysView.readControlButton()) || mVirtualControlKeyDown
  }

  override fun readAltKey(): Boolean {
    val extraKeysView = termSessionData?.extraKeysView
    return (extraKeysView != null && extraKeysView.readAltButton()) || mVirtualFnKeyDown
  }

  override fun readShiftKey(): Boolean {
    val extraKeysView = termSessionData?.extraKeysView
    return (extraKeysView != null && extraKeysView.readShiftButton()) || mVirtualShiftKeyDown
  }

  override fun readFnKey(): Boolean {
    val extraKeysView = termSessionData?.extraKeysView
    return (extraKeysView != null && extraKeysView.readFnButton()) || mVirtualFnKeyDown
  }

  override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
    if (mVirtualFnKeyDown) {
      var resultingKeyCode: Int = -1
      var resultingCodePoint: Int = -1
      var altDown = false
      val lowerCase = Character.toLowerCase(codePoint)
      when (lowerCase.toChar()) {
        'w' -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_UP
        'a' -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_LEFT
        's' -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_DOWN
        'd' -> resultingKeyCode = KeyEvent.KEYCODE_DPAD_RIGHT

        'p' -> resultingKeyCode = KeyEvent.KEYCODE_PAGE_UP
        'n' -> resultingKeyCode = KeyEvent.KEYCODE_PAGE_DOWN

        't' -> resultingKeyCode = KeyEvent.KEYCODE_TAB
        'i' -> resultingKeyCode = KeyEvent.KEYCODE_INSERT
        'h' -> resultingCodePoint = '~'.toInt()

        'u' -> resultingCodePoint = '_'.toInt()
        'l' -> resultingCodePoint = '|'.toInt()

        '1', '2', '3', '4', '5', '6', '7', '8', '9' -> resultingKeyCode = codePoint - '1'.toInt() + KeyEvent.KEYCODE_F1
        '0' -> resultingKeyCode = KeyEvent.KEYCODE_F10

        'e' -> resultingCodePoint = 27
        '.' -> resultingCodePoint = 28

        'b'
          , 'f'
          , 'x'
        -> {
          resultingCodePoint = lowerCase
          altDown = true
        }

        'v' -> {
          resultingCodePoint = -1
          val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
          audio.adjustSuggestedStreamVolume(
            AudioManager.ADJUST_SAME,
            AudioManager.USE_DEFAULT_STREAM_TYPE,
            AudioManager.FLAG_SHOW_UI
          )
        }
      }

      if (resultingKeyCode != -1) {
        if (session != null) {
          val term = session.emulator
          session.write(
            KeyHandler.getCode(
              resultingKeyCode,
              0,
              term.isCursorKeysApplicationMode,
              term.isKeypadApplicationMode
            )
          )
        }
      } else if (resultingCodePoint != -1) {
        session?.writeCodePoint(altDown, resultingCodePoint)
      }
      return true
    }
    return false
  }

  override fun onLongPress(event: MotionEvent?): Boolean {
    return false
  }

  private fun handleVirtualKeys(keyCode: Int, event: KeyEvent?, down: Boolean): Boolean {
    if (event == null) {
      return false
    }

    val shellSession = termSessionData?.termSession as ShellTermSession? ?: return false

    val volumeAsSpecialKeys = shellSession.shellProfile.enableSpecialVolumeKeys

    val inputDevice = event.device
    if (inputDevice != null && inputDevice.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC) {
      return false
    } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
      mVirtualControlKeyDown = down && volumeAsSpecialKeys
      return true
    } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      mVirtualFnKeyDown = down && volumeAsSpecialKeys
      return true
    }
    return false
  }

  fun updateExtraKeys(title: String?, force: Boolean = false) {
    val extraKeysView = termSessionData?.extraKeysView

    if (extraKeysView == null || title == null || title.isEmpty()) {
      return
    }

    if ((lastTitle != title || force)
      && updateExtraKeysVisibility()
    ) {
      removeExtraKeys()
      ComponentManager.getComponent<ExtraKeyComponent>().showShortcutKeys(title, extraKeysView)
      extraKeysView.updateButtons()
      lastTitle = title
    }
  }

  private fun updateExtraKeysVisibility(): Boolean {
    val extraKeysView = termSessionData?.extraKeysView ?: return false
    val shellSession = termSessionData?.termSession as ShellTermSession? ?: return false

    return if (shellSession.shellProfile.enableExtraKeys) {
      extraKeysView.visibility = View.VISIBLE
      true
    } else {
      extraKeysView.visibility = View.GONE
      false
    }
  }

  private fun removeExtraKeys() {
    val extraKeysView = termSessionData?.extraKeysView
    extraKeysView?.clearUserKeys()
  }

  private fun changeFontSize(increase: Boolean) {
    val termView = termSessionData?.termView
    if (termView != null) {
      val changedSize = (if (increase) 1 else -1) * 2
      val fontSize = NeoPreference.validateFontSize(termView.textSize + changedSize)
      termView.textSize = fontSize
      NeoPreference.store(NeoPreference.KEY_FONT_SIZE, fontSize)
    }
  }
}

class TermSessionCallback : TerminalSession.SessionChangedCallback {
  var termSessionData: TermSessionData? = null

  var bellController: BellController? = null

  override fun onTextChanged(changedSession: TerminalSession?) {
    termSessionData?.termView?.onScreenUpdated()
  }

  override fun onTitleChanged(changedSession: TerminalSession?) {
    if (changedSession?.title != null) {
      termSessionData?.termUI?.requireUpdateTitle(changedSession.title)
    }
  }

  override fun onSessionFinished(finishedSession: TerminalSession?) {
    termSessionData?.termUI?.requireOnSessionFinished()
  }

  override fun onClipboardText(session: TerminalSession?, text: String?) {
    val termView = termSessionData?.termView
    if (termView != null) {
      val clipboardManager = termView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val clipData = ClipData.newPlainText("", text)
      clipboardManager.setPrimaryClip(clipData)
    }
  }

  override fun onBell(session: TerminalSession?) {
    val termView = termSessionData?.termView ?: return
    val shellSession = session as ShellTermSession

    if (bellController == null) {
      bellController = BellController()
    }

    bellController?.bellOrVibrate(termView.context, shellSession)
  }

  override fun onColorsChanged(session: TerminalSession?) {
    val termView = termSessionData?.termView
    if (session != null && termView != null) {
      termView.onScreenUpdated()
    }
  }
}

class BellController {
  companion object {
    private val BELL_DELAY_MS = 100
  }

  private var bellId: Int = 0
  private var soundPool: SoundPool? = null
  private var lastBellTime = 0L

  fun bellOrVibrate(context: Context, session: ShellTermSession) {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastBellTime < BELL_DELAY_MS) {
      return
    }
    lastBellTime = currentTime

    if (session.shellProfile.enableBell) {
      if (soundPool == null) {
        soundPool = SoundPool.Builder().setMaxStreams(1).build()
        bellId = soundPool!!.load(context, R.raw.bell, 1)
      }
      soundPool?.play(bellId, 1f, 1f, 0, 0, 1f)
    }
  }
}

class TermCompleteListener(var terminalView: TerminalView?) : OnAutoCompleteListener, OnCandidateSelectedListener {
  private val inputStack = Stack<Char>()
  private var popupWindow: CandidatePopupWindow? = null
  private var lastCompletedIndex = 0

  override fun onKeyCode(keyCode: Int, keyMod: Int) {
    when (keyCode) {
      KeyEvent.KEYCODE_DEL -> {
        popChar()
        fixLastCompletedIndex()
        triggerCompletion()
      }

      KeyEvent.KEYCODE_ENTER -> {
        clearChars()
        popupWindow?.dismiss()
      }
    }
  }

  private fun fixLastCompletedIndex() {
    val currentText = getCurrentEditingText()
    lastCompletedIndex = minOf(lastCompletedIndex, currentText.length - 1)
  }

  override fun onCompletionRequired(newText: String?) {
    if (newText == null || newText.isEmpty()) {
      return
    }
    pushString(newText)
    triggerCompletion()
  }

  override fun onCleanUp() {
    popupWindow?.dismiss()
    popupWindow?.cleanup()
    popupWindow = null
    terminalView = null
  }

  override fun onFinishCompletion(): Boolean {
    val popWindow = popupWindow ?: return false

    if (popWindow.isShowing()) {
      popWindow.dismiss()
      return true
    }
    return false
  }

  override fun onCandidateSelected(candidate: CompletionCandidate) {
    val session = terminalView?.currentSession ?: return
    val textNeedCompletion = getCurrentEditingText().substring(lastCompletedIndex + 1)
    val newText = candidate.completeString

    val deleteLength = newText.indexOf(textNeedCompletion) + textNeedCompletion.length
    if (deleteLength > 0) {
      for (i in 0 until deleteLength) {
        session.write("\b")
        popChar()
      }
    }

    if (BuildConfig.DEBUG) {
      Log.e(
        "NeoTerm-AC",
        "currentEditing: $textNeedCompletion, " +
          "deleteLength: $deleteLength, completeString: $newText"
      )
    }

    pushString(newText)
    session.write(newText)
    lastCompletedIndex = inputStack.size
    triggerCompletion()
  }

  private fun triggerCompletion() {
    val text = getCurrentEditingText()
    if (text.isEmpty()) {
      return
    }

    val result = CompletionManager.tryCompleteFor(text)
    if (!result.hasResult()) {
      result.markScore(0)
      onFinishCompletion()
      return
    }
    showAutoCompleteCandidates(result)
  }

  private fun showAutoCompleteCandidates(result: CompletionResult) {
    val termView = terminalView
    var popWindow = popupWindow

    if (termView == null) {
      return
    }

    if (popWindow == null) {
      popWindow = CandidatePopupWindow(termView.context)
      popWindow.onCandidateSelectedListener = this
      this.popupWindow = popWindow
    }

    popWindow.candidates = result.candidates
    popWindow.show(termView)
  }

  private fun getCurrentEditingText(): String {
    val builder = StringBuilder()
    val size = inputStack.size
    var start = inputStack.lastIndexOf(' ')
    if (start < 0) {
      start = -1
    }

    IntRange(start + 1, size - 1)
      .map { inputStack[it] }
      .takeWhile { !(it == 0.toChar() || it == ' ') }
      .forEach { builder.append(it) }
    return builder.toString()
  }

  private fun clearChars() {
    inputStack.clear()
    lastCompletedIndex = 0
  }

  private fun popChar() {
    if (inputStack.isNotEmpty()) {
      inputStack.pop()
    }
  }

  private fun pushString(string: String) {
    string.toCharArray().forEach { pushChar(it) }
  }

  private fun pushChar(char: Char) {
    inputStack.push(char)
  }
}
