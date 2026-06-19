package com.stryker.terminal.frontend.session.view;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Scroller;
import android.widget.Toast;

import com.stryker.terminal.backend.EmulatorDebug;
import com.stryker.terminal.backend.KeyHandler;
import com.stryker.terminal.backend.TerminalBuffer;
import com.stryker.terminal.backend.TerminalEmulator;
import com.stryker.terminal.backend.TerminalSession;
import com.stryker.terminal.component.completion.OnAutoCompleteListener;
import com.stryker.terminal.frontend.session.terminal.OnTextSize;

import com.stryker.terminal.R;

public final class TerminalView extends View {

  private static final boolean LOG_KEY_EVENTS = false;

  TerminalSession mTermSession;

  TerminalEmulator mEmulator;

  TerminalRenderer mRenderer;

  TerminalViewClient mClient;

  int mTopRow;

  boolean mIsSelectingText = false, mIsDraggingLeftSelection, mInitialTextSelection;
  int mSelX1 = -1, mSelX2 = -1, mSelY1 = -1, mSelY2 = -1;
  float mSelectionDownX, mSelectionDownY;
  private ActionMode mActionMode;
  private BitmapDrawable mLeftSelectionHandle, mRightSelectionHandle;
  private static Toast toast;
  private OnTextSize onTextSize;
  private Context context;

  float mScaleFactor = 1.f;
  GestureAndScaleRecognizer mGestureRecognizer;

  private int mMouseScrollStartX = -1, mMouseScrollStartY = -1;
  private long mMouseStartDownTime = -1;

  Scroller mScroller;

  float mScrollRemainder;

  int mCombiningAccent;
  int mTextSize;

  private boolean mEnableWordBasedIme = false;

  private boolean mAccessibilityEnabled;

  public final static int KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD = KeyCharacterMap.VIRTUAL_KEYBOARD;

  public final static int KEY_EVENT_SOURCE_SOFT_KEYBOARD = 0;

  public TerminalView(Context context) {
    super(context);
    commonInit(context);
  }

  public TerminalView(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
    commonInit(context);
  }

  private void commonInit(Context context) {
    this.context = context;
    mGestureRecognizer = new GestureAndScaleRecognizer(context, new GestureAndScaleRecognizer.Listener() {

      private boolean scrolledWithFinger;

      private float doubleTapX, doubleTapY;
      private boolean draggedAfterDoubleTap;

      @Override
      public boolean onUp(MotionEvent e) {
        mScrollRemainder = 0.0f;
        if (mEmulator != null && mEmulator.isMouseTrackingActive() && !mIsSelectingText && !scrolledWithFinger) {
          sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, true);
          sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, false);
          return true;
        }
        scrolledWithFinger = false;
        return false;
      }

      @Override
      public boolean onSingleTapUp(MotionEvent e) {
        if (mEmulator == null) return true;
        if (mIsSelectingText) {
          toggleSelectingText(null);
          return true;
        }
        requestFocus();
        if (!mEmulator.isMouseTrackingActive()) {
          if (!e.isFromSource(InputDevice.SOURCE_MOUSE)) {
            mClient.onSingleTapUp(e);
            return true;
          }
        }
        return false;
      }

      @Override
      public boolean onScroll(MotionEvent e, float distanceX, float distanceY) {
        if (mEmulator == null || mIsSelectingText) return true;

        if (mEmulator.isMouseTrackingActive() && e.isFromSource(InputDevice.SOURCE_MOUSE)) {
          sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
        } else {
          scrolledWithFinger = true;
          distanceY += mScrollRemainder;
          int deltaRows = (int) (distanceY / mRenderer.mFontLineSpacing);

          mScrollRemainder = distanceY - deltaRows * mRenderer.mFontLineSpacing;
          doScroll(e, deltaRows);
        }
        return true;
      }

      @Override
      public boolean onScale(float focusX, float focusY, float scale) {
        if (mEmulator == null || mIsSelectingText) return true;
        mScaleFactor *= scale;
        mScaleFactor = mClient.onScale(mScaleFactor);
        return true;
      }

      @Override
      public boolean onFling(final MotionEvent e2, float velocityX, float velocityY) {
        if (mEmulator == null || mIsSelectingText) return true;

        if (!mScroller.isFinished()) return true;

        final boolean mouseTrackingAtStartOfFling = mEmulator.isMouseTrackingActive();
        float SCALE = 0.25f;
        if (mouseTrackingAtStartOfFling) {
          mScroller.fling(0, 0, 0, -(int) (velocityY * SCALE), 0, 0, -mEmulator.mRows / 2, mEmulator.mRows / 2);
        } else {
          mScroller.fling(0, mTopRow, 0, -(int) (velocityY * SCALE), 0, 0, -mEmulator.getScreen().getActiveTranscriptRows(), 0);
        }

        post(new Runnable() {
          private int mLastY = 0;

          @Override
          public void run() {
            if (mouseTrackingAtStartOfFling != mEmulator.isMouseTrackingActive()) {
              mScroller.abortAnimation();
              return;
            }
            if (mScroller.isFinished()) return;
            boolean more = mScroller.computeScrollOffset();
            int newY = mScroller.getCurrY();
            int diff = mouseTrackingAtStartOfFling ? (newY - mLastY) : (newY - mTopRow);
            doScroll(e2, diff);
            mLastY = newY;
            if (more) post(this);
          }
        });

        return true;
      }

      @Override
      public boolean onDown(float x, float y) {
        return false;
      }

      @Override
      public boolean onDoubleTap(MotionEvent e) {
        return true;
      }

      @Override
      public boolean onDoubleTapEvent(MotionEvent e) {
        if (mEmulator.isMouseTrackingActive() && !e.isFromSource(InputDevice.SOURCE_MOUSE)) {
          switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
              doubleTapX = e.getX();
              doubleTapY = e.getY();
              draggedAfterDoubleTap = false;
              sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, true);
              break;
            case MotionEvent.ACTION_UP:
              if (!draggedAfterDoubleTap) {
                sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, false);
                sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, true);
              }
              sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON, false);
              break;
            case MotionEvent.ACTION_MOVE:
              if (Math.abs(e.getX() - doubleTapX) >= mRenderer.mFontWidth
                || Math.abs(e.getY() - doubleTapY) >= mRenderer.mFontLineSpacing) {
                doubleTapX = e.getX();
                doubleTapY = e.getY();
                draggedAfterDoubleTap = true;
                sendMouseEventCode(e, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
              }
              break;
          }
        }
        return true;
      }

      @Override
      public void onLongPress(MotionEvent e) {
        if (mGestureRecognizer.isInProgress()) return;
        if (mClient.onLongPress(e)) return;
        if (!mIsSelectingText) {
          performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
          toggleSelectingText(e);
        }
      }
    });
    mScroller = new Scroller(context);
    AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    mAccessibilityEnabled = am != null && am.isEnabled();
  }

  public void setTerminalViewClient(TerminalViewClient client) {
    this.mClient = client;
  }

  @Override
  public void setOnKeyListener(OnKeyListener l) {
    if (l instanceof TerminalViewClient) {
      setTerminalViewClient(((TerminalViewClient) l));
    }
  }

  public boolean attachSession(TerminalSession session) {
    if (session == mTermSession) return false;
    mTopRow = 0;

    mTermSession = session;
    mEmulator = null;
    mCombiningAccent = 0;

    updateSize();

    setVerticalScrollBarEnabled(true);

    return true;
  }

  @Override
  public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
    if (mEnableWordBasedIme) {
      outAttrs.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
    } else {
      outAttrs.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL | InputType.TYPE_NULL;
    }

    outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN;

    return new BaseInputConnection(this, true) {
      @Override
      public boolean finishComposingText() {
        if (LOG_KEY_EVENTS) Log.i(EmulatorDebug.LOG_TAG, "IME: finishComposingText()");
        super.finishComposingText();

        sendTextToTerminal(getEditable());
        getEditable().clear();
        return true;
      }

      @Override
      public boolean commitText(CharSequence text, int newCursorPosition) {
        if (LOG_KEY_EVENTS) {
          Log.i(EmulatorDebug.LOG_TAG, "IME: commitText(\"" + text + "\", " + newCursorPosition + ")");
        }
        super.commitText(text, newCursorPosition);

        if (mEmulator == null) return true;

        Editable content = getEditable();
        sendTextToTerminal(content);
        if (onAutoCompleteListener != null) {
          onAutoCompleteListener.onCompletionRequired(content.toString());
        }
        content.clear();
        return true;
      }

      @Override
      public boolean deleteSurroundingText(int leftLength, int rightLength) {
        if (LOG_KEY_EVENTS) {
          Log.i(EmulatorDebug.LOG_TAG, "IME: deleteSurroundingText(" + leftLength + ", " + rightLength + ")");
        }
        KeyEvent deleteKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL);
        for (int i = 0; i < leftLength; i++) sendKeyEvent(deleteKey);
        return super.deleteSurroundingText(leftLength, rightLength);
      }

      void sendTextToTerminal(CharSequence text) {
        final int textLengthInChars = text.length();
        for (int i = 0; i < textLengthInChars; i++) {
          char firstChar = text.charAt(i);
          int codePoint;
          if (Character.isHighSurrogate(firstChar)) {
            if (++i < textLengthInChars) {
              codePoint = Character.toCodePoint(firstChar, text.charAt(i));
            } else {
              codePoint = TerminalEmulator.UNICODE_REPLACEMENT_CHAR;
            }
          } else {
            codePoint = firstChar;
          }

          boolean ctrlHeld = false;
          if (codePoint <= 31 && codePoint != 27) {
            if (codePoint == '\n') {
              codePoint = '\r';
            }

            ctrlHeld = true;
            switch (codePoint) {
              case 31:
                codePoint = '_';
                break;
              case 30:
                codePoint = '^';
                break;
              case 29:
                codePoint = ']';
                break;
              case 28:
                codePoint = '\\';
                break;
              default:
                codePoint += 96;
                break;
            }
          }

          inputCodePoint(KEY_EVENT_SOURCE_SOFT_KEYBOARD, codePoint, ctrlHeld, false);
        }
      }

    };
  }

  @Override
  protected int computeVerticalScrollRange() {
    return mEmulator == null ? 1 : mEmulator.getScreen().getActiveRows();
  }

  @Override
  protected int computeVerticalScrollExtent() {
    return mEmulator == null ? 1 : mEmulator.mRows;
  }

  @Override
  protected int computeVerticalScrollOffset() {
    return mEmulator == null ? 1 : mEmulator.getScreen().getActiveRows() + mTopRow - mEmulator.mRows;
  }

  public void onScreenUpdated() {
    if (mEmulator == null) return;
    boolean skipScrolling = false;
    boolean isScreenHeld = false;

    if (mTopRow != 0) {
      isScreenHeld = true;
    }

    int rowsInHistory = mEmulator.getScreen().getActiveTranscriptRows();
    if (mTopRow < -rowsInHistory) mTopRow = -rowsInHistory;

    if (mIsSelectingText || isScreenHeld) {
      int rowShift = mEmulator.getScrollCounter();
      if (-mTopRow + rowShift > rowsInHistory) {
        if (mIsSelectingText) {
          toggleSelectingText(null);
        }
      } else {
        skipScrolling = true;
        mTopRow -= rowShift;
        mSelY1 -= rowShift;
        mSelY2 -= rowShift;
      }

      if (isScreenHeld) {
        awakenScrollBars();
      }
    }

    if (!skipScrolling && mTopRow != 0) {
      if (mTopRow < -3) {
        awakenScrollBars();
      }
      mTopRow = 0;
    }

    mEmulator.clearScrollCounter();
    invalidate();

    String contentText = mEmulator.getScreen()
      .getSelectedText(0, mTopRow, mEmulator.mColumns, mTopRow + mEmulator.mRows);
    if (mAccessibilityEnabled) {
      setContentDescription(contentText);
    }
  }

  public int getTextSize() {
    return mTextSize;
  }

  public void setTextSize(int textSize) {
    this.mTextSize = textSize;
    mRenderer = new TerminalRenderer(textSize, mRenderer == null ? Typeface.MONOSPACE : mRenderer.mTypeface);
    updateSize();

    if (onTextSize != null)
      onTextSize.onTextSize(getTextSize());
  }

  public void setTypeface(Typeface newTypeface) {
    mRenderer = new TerminalRenderer(mRenderer.mTextSize, newTypeface);
    updateSize();
    invalidate();
  }

  @Override
  public boolean onCheckIsTextEditor() {
    return true;
  }

  @Override
  public boolean isOpaque() {
    return true;
  }

  void sendMouseEventCode(MotionEvent e, int button, boolean pressed) {
    int x = (int) (e.getX() / mRenderer.mFontWidth) + 1;
    int y = (int) ((e.getY() - mRenderer.mFontLineSpacingAndAscent) / mRenderer.mFontLineSpacing) + 1;
    if (pressed && (button == TerminalEmulator.MOUSE_WHEELDOWN_BUTTON || button == TerminalEmulator.MOUSE_WHEELUP_BUTTON)) {
      if (mMouseStartDownTime == e.getDownTime()) {
        x = mMouseScrollStartX;
        y = mMouseScrollStartY;
      } else {
        mMouseStartDownTime = e.getDownTime();
        mMouseScrollStartX = x;
        mMouseScrollStartY = y;
      }
    }
    mEmulator.sendMouseEvent(button, x, y, pressed);
  }

  void doScroll(MotionEvent event, int rowsDown) {
    boolean up = rowsDown < 0;
    int amount = Math.abs(rowsDown);
    for (int i = 0; i < amount; i++) {
      if (mEmulator.isMouseTrackingActive()) {
        sendMouseEventCode(event, up ? TerminalEmulator.MOUSE_WHEELUP_BUTTON : TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, true);
      } else if (mEmulator.isAlternateBufferActive()) {
        handleKeyCode(up ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN, 0);
      } else {
        mTopRow = Math.min(0, Math.max(-(mEmulator.getScreen().getActiveTranscriptRows()), mTopRow + (up ? -1 : 1)));
        if (!awakenScrollBars()) invalidate();
      }
    }
  }

  @Override
  public boolean onGenericMotionEvent(MotionEvent event) {
    if (mEmulator != null && event.isFromSource(InputDevice.SOURCE_MOUSE) && event.getAction() == MotionEvent.ACTION_SCROLL) {
      boolean up = event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0.0f;
      doScroll(event, up ? -3 : 3);
      return true;
    }
    return false;
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  @TargetApi(23)
  public boolean onTouchEvent(MotionEvent ev) {
    if (mEmulator == null) return true;
    final int action = ev.getAction();

    if (mIsSelectingText) {
      int cy = (int) (ev.getY() / mRenderer.mFontLineSpacing) + mTopRow;
      int cx = (int) (ev.getX() / mRenderer.mFontWidth);

      switch (action) {
        case MotionEvent.ACTION_UP:
          mInitialTextSelection = false;
          break;
        case MotionEvent.ACTION_DOWN:
          int distanceFromSel1 = Math.abs(cx - mSelX1) + Math.abs(cy - mSelY1);
          int distanceFromSel2 = Math.abs(cx - mSelX2) + Math.abs(cy - mSelY2);
          mIsDraggingLeftSelection = distanceFromSel1 <= distanceFromSel2;
          mSelectionDownX = ev.getX();
          mSelectionDownY = ev.getY();
          break;
        case MotionEvent.ACTION_MOVE:
          if (mInitialTextSelection) break;
          float deltaX = ev.getX() - mSelectionDownX;
          float deltaY = ev.getY() - mSelectionDownY;
          int deltaCols = (int) Math.ceil(deltaX / mRenderer.mFontWidth);
          int deltaRows = (int) Math.ceil(deltaY / mRenderer.mFontLineSpacing);
          mSelectionDownX += deltaCols * mRenderer.mFontWidth;
          mSelectionDownY += deltaRows * mRenderer.mFontLineSpacing;
          if (mIsDraggingLeftSelection) {
            mSelX1 += deltaCols;
            mSelY1 += deltaRows;
          } else {
            mSelX2 += deltaCols;
            mSelY2 += deltaRows;
          }

          mSelX1 = Math.min(mEmulator.mColumns, Math.max(0, mSelX1));
          mSelX2 = Math.min(mEmulator.mColumns, Math.max(0, mSelX2));

          if (mSelY1 == mSelY2 && mSelX1 > mSelX2 || mSelY1 > mSelY2) {
            mIsDraggingLeftSelection = !mIsDraggingLeftSelection;
            int tmpX1 = mSelX1, tmpY1 = mSelY1;
            mSelX1 = mSelX2;
            mSelY1 = mSelY2;
            mSelX2 = tmpX1;
            mSelY2 = tmpY1;
          }

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            mActionMode.invalidateContentRect();
          invalidate();
          break;
        default:
          break;
      }
      mGestureRecognizer.onTouchEvent(ev);
      return true;
    } else if (ev.isFromSource(InputDevice.SOURCE_MOUSE)) {
      if (ev.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) {
        if (action == MotionEvent.ACTION_DOWN) showContextMenu();
        return true;
      } else if (ev.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) {
        pasteFromClipboard();
      } else if (mEmulator.isMouseTrackingActive()) {
        switch (ev.getAction()) {
          case MotionEvent.ACTION_DOWN:
          case MotionEvent.ACTION_UP:
            sendMouseEventCode(ev, TerminalEmulator.MOUSE_LEFT_BUTTON, ev.getAction() == MotionEvent.ACTION_DOWN);
            break;
          case MotionEvent.ACTION_MOVE:
            sendMouseEventCode(ev, TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, true);
            break;
        }
        return true;
      }
    }

    mGestureRecognizer.onTouchEvent(ev);
    return true;
  }

  public void pasteFromClipboard() {
    ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard == null) {
      return;
    }
    ClipData clipData = clipboard.getPrimaryClip();
    if (clipData != null) {
      CharSequence paste = clipData.getItemAt(0).coerceToText(getContext());
      if (!TextUtils.isEmpty(paste)) mEmulator.paste(paste.toString());
    }
  }

  @Override
  public boolean onKeyPreIme(int keyCode, KeyEvent event) {
    if (LOG_KEY_EVENTS)
      Log.i(EmulatorDebug.LOG_TAG, "onKeyPreIme(keyCode=" + keyCode + ", event=" + event + ")");
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (mIsSelectingText) {
        toggleSelectingText(null);
        return true;
      } else if (mClient.shouldBackButtonBeMappedToEscape()) {
        switch (event.getAction()) {
          case KeyEvent.ACTION_DOWN:
            return onKeyDown(keyCode, event);
          case KeyEvent.ACTION_UP:
            return onKeyUp(keyCode, event);
        }
      }
    }
    return super.onKeyPreIme(keyCode, event);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (LOG_KEY_EVENTS)
      Log.i(EmulatorDebug.LOG_TAG, "onKeyDown(keyCode=" + keyCode + ", isSystem()=" + event.isSystem() + ", event=" + event + ")");
    if (mEmulator == null) return true;

    if (mClient.onKeyDown(keyCode, event, mTermSession)) {
      invalidate();
      return true;
    } else if (event.isSystem() && (!mClient.shouldBackButtonBeMappedToEscape() || keyCode != KeyEvent.KEYCODE_BACK)) {
      return super.onKeyDown(keyCode, event);
    } else if (event.getAction() == KeyEvent.ACTION_MULTIPLE && keyCode == KeyEvent.KEYCODE_UNKNOWN) {
      mTermSession.write(event.getCharacters());
      return true;
    }

    final int metaState = event.getMetaState();
    final boolean controlDown = event.isCtrlPressed() || mClient.readControlKey();
    final boolean leftAltDown = (metaState & KeyEvent.META_ALT_LEFT_ON) != 0 || mClient.readAltKey();
    final boolean shiftDown = event.isShiftPressed() || mClient.readShiftKey();
    final boolean rightAltDownFromEvent = (metaState & KeyEvent.META_ALT_RIGHT_ON) != 0;

    int keyMod = 0;
    if (controlDown) keyMod |= KeyHandler.KEYMOD_CTRL;
    if (event.isAltPressed() || leftAltDown) keyMod |= KeyHandler.KEYMOD_ALT;
    if (shiftDown) keyMod |= KeyHandler.KEYMOD_SHIFT;
    if (event.isNumLockOn()) keyMod |= KeyHandler.KEYMOD_NUM_LOCK;
    if (!event.isFunctionPressed() && handleKeyCode(keyCode, keyMod)) {
      if (LOG_KEY_EVENTS) Log.i(EmulatorDebug.LOG_TAG, "handleKeyCode() took key event");
      return true;
    }

    int bitsToClear = KeyEvent.META_CTRL_MASK;
    if (rightAltDownFromEvent) {
    } else {
      bitsToClear |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
    }
    int effectiveMetaState = event.getMetaState() & ~bitsToClear;

    int result = event.getUnicodeChar(effectiveMetaState);
    if (LOG_KEY_EVENTS)
      Log.i(EmulatorDebug.LOG_TAG, "KeyEvent#getUnicodeChar(" + effectiveMetaState + ") returned: " + result);
    if (result == 0) {
      return false;
    }

    int oldCombiningAccent = mCombiningAccent;
    if ((result & KeyCharacterMap.COMBINING_ACCENT) != 0) {
      if (mCombiningAccent != 0)
        inputCodePoint(event.getDeviceId(), mCombiningAccent, controlDown, leftAltDown);
      mCombiningAccent = result & KeyCharacterMap.COMBINING_ACCENT_MASK;
    } else {
      if (mCombiningAccent != 0) {
        int combinedChar = KeyCharacterMap.getDeadChar(mCombiningAccent, result);
        if (combinedChar > 0) result = combinedChar;
        mCombiningAccent = 0;
      }
      inputCodePoint(event.getDeviceId(), result, controlDown, leftAltDown);
    }

    if (mCombiningAccent != oldCombiningAccent) invalidate();

    if (onAutoCompleteListener != null) {
      if (event.isPrintingKey()) {
        char printingChar = (char) event.getUnicodeChar(metaState);
        if (printingChar != '\b') {
          onAutoCompleteListener.onCompletionRequired(new String(new char[]{printingChar}));
        }
      }
    }

    return true;
  }

  public void inputCodePoint(int eventSource, int codePoint, boolean controlDownFromEvent, boolean leftAltDownFromEvent) {
    if (LOG_KEY_EVENTS) {
      Log.i(EmulatorDebug.LOG_TAG, "inputCodePoint(codePoint=" + codePoint + ", controlDownFromEvent=" + controlDownFromEvent + ", leftAltDownFromEvent="
        + leftAltDownFromEvent + ")");
    }

    if (mTermSession == null) return;

    final boolean controlDown = controlDownFromEvent || mClient.readControlKey();
    final boolean altDown = leftAltDownFromEvent || mClient.readAltKey();

    if (mClient.onCodePoint(codePoint, controlDown, mTermSession)) return;

    if (controlDown) {
      if (codePoint >= 'a' && codePoint <= 'z') {
        codePoint = codePoint - 'a' + 1;
      } else if (codePoint >= 'A' && codePoint <= 'Z') {
        codePoint = codePoint - 'A' + 1;
      } else if (codePoint == ' ' || codePoint == '2') {
        codePoint = 0;
      } else if (codePoint == '[' || codePoint == '3') {
        codePoint = 27;
      } else if (codePoint == '\\' || codePoint == '4') {
        codePoint = 28;
      } else if (codePoint == ']' || codePoint == '5') {
        codePoint = 29;
      } else if (codePoint == '^' || codePoint == '6') {
        codePoint = 30;
      } else if (codePoint == '_' || codePoint == '7' || codePoint == '/') {
        codePoint = 31;
      } else if (codePoint == '8') {
        codePoint = 127;
      }
    }

    if (codePoint > -1) {
      if (eventSource > KEY_EVENT_SOURCE_SOFT_KEYBOARD) {
        switch (codePoint) {
          case 0x02DC:
            codePoint = 0x007E;
            break;
          case 0x02CB:
            codePoint = 0x0060;
            break;
          case 0x02C6:
            codePoint = 0x005E;
            break;
        }
      }

      mTermSession.writeCodePoint(altDown, codePoint);
      scrollToBottomIfNeeded();
    }
  }

  public boolean handleKeyCode(int keyCode, int keyMod) {
    TerminalEmulator term = mTermSession.getEmulator();
    String code = KeyHandler.getCode(keyCode, keyMod, term.isCursorKeysApplicationMode(), term.isKeypadApplicationMode());
    if (code == null) return false;
    mTermSession.write(code);
    scrollToBottomIfNeeded();
    if (onAutoCompleteListener != null) {
      onAutoCompleteListener.onKeyCode(keyCode, keyMod);
    }
    return true;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (LOG_KEY_EVENTS)
      Log.i(EmulatorDebug.LOG_TAG, "onKeyUp(keyCode=" + keyCode + ", event=" + event + ")");
    if (mEmulator == null) return true;

    if (mClient.onKeyUp(keyCode, event)) {
      invalidate();
      return true;
    } else if (event.isSystem()) {
      return super.onKeyUp(keyCode, event);
    }

    return true;
  }

  void scrollToBottomIfNeeded() {
    if (mTopRow != 0) {
      mTopRow = 0;
      mEmulator.clearScrollCounter();
      invalidate();
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    updateSize();
  }

  public void updateSize() {
    int viewWidth = getWidth();
    int viewHeight = getHeight();
    if (viewWidth == 0 || viewHeight == 0 || mTermSession == null) return;


    int newColumns = Math.max(4, (int) (viewWidth / mRenderer.mFontWidth));
    int newRows = Math.max(4, (viewHeight - mRenderer.mFontLineSpacingAndAscent) / mRenderer.mFontLineSpacing);

    if (mEmulator == null || (newColumns != mEmulator.mColumns || newRows != mEmulator.mRows)) {
      mTermSession.updateSize(newColumns, newRows);
      mEmulator = mTermSession.getEmulator();

      mTopRow = 0;
      scrollTo(0, 0);
      invalidate();
    }

  }

  @Override
  protected void onDraw(Canvas canvas) {
    if (mEmulator == null) {
      canvas.drawColor(0XFF000000);
    } else {
      mRenderer.render(mEmulator, canvas, mTopRow, mSelY1, mSelY2, mSelX1, mSelX2);

      if (mIsSelectingText) {
        final int gripHandleWidth = mLeftSelectionHandle.getIntrinsicWidth();
        final int gripHandleMargin = gripHandleWidth / 4;

        int right = Math.round((mSelX1) * mRenderer.mFontWidth) + gripHandleMargin;
        int top = (mSelY1 + 1 - mTopRow) * mRenderer.mFontLineSpacing + mRenderer.mFontLineSpacingAndAscent;
        mLeftSelectionHandle.setBounds(right - gripHandleWidth, top, right, top + mLeftSelectionHandle.getIntrinsicHeight());
        mLeftSelectionHandle.draw(canvas);

        int left = Math.round((mSelX2 + 1) * mRenderer.mFontWidth) - gripHandleMargin;
        top = (mSelY2 + 1 - mTopRow) * mRenderer.mFontLineSpacing + mRenderer.mFontLineSpacingAndAscent;
        mRightSelectionHandle.setBounds(left, top, left + gripHandleWidth, top + mRightSelectionHandle.getIntrinsicHeight());
        mRightSelectionHandle.draw(canvas);
      }
    }
  }

  @TargetApi(23)
  public void toggleSelectingText(MotionEvent ev) {
    mIsSelectingText = !mIsSelectingText;
    mClient.copyModeChanged(mIsSelectingText);

    if (mIsSelectingText) {
      if (mLeftSelectionHandle == null) {
        mLeftSelectionHandle = (BitmapDrawable) getContext().getDrawable(R.drawable.text_select_handle_left_material);
        mRightSelectionHandle = (BitmapDrawable) getContext().getDrawable(R.drawable.text_select_handle_right_material);
      }

      int cx = (int) (ev.getX() / mRenderer.mFontWidth);
      final boolean eventFromMouse = ev.isFromSource(InputDevice.SOURCE_MOUSE);
      final int SELECT_TEXT_OFFSET_Y = eventFromMouse ? 0 : -40;
      int cy = (int) ((ev.getY() + SELECT_TEXT_OFFSET_Y) / mRenderer.mFontLineSpacing) + mTopRow;

      mSelX1 = mSelX2 = cx;
      mSelY1 = mSelY2 = cy;

      TerminalBuffer screen = mEmulator.getScreen();
      if (!" ".equals(screen.getSelectedText(mSelX1, mSelY1, mSelX1, mSelY1))) {
        while (mSelX1 > 0 && !"".equals(screen.getSelectedText(mSelX1 - 1, mSelY1, mSelX1 - 1, mSelY1))) {
          mSelX1--;
        }
        while (mSelX2 < mEmulator.mColumns - 1 && !"".equals(screen.getSelectedText(mSelX2 + 1, mSelY1, mSelX2 + 1, mSelY1))) {
          mSelX2++;
        }
      }

      mInitialTextSelection = true;
      mIsDraggingLeftSelection = true;
      mSelectionDownX = ev.getX();
      mSelectionDownY = ev.getY();

      final ActionMode.Callback callback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
          int show = MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT;

          ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
          menu.add(Menu.NONE, 1, Menu.NONE, R.string.copy_text).setShowAsAction(show);
          menu.add(Menu.NONE, 2, Menu.NONE, R.string.paste_text).setEnabled(clipboard.hasPrimaryClip()).setShowAsAction(show);
          menu.add(Menu.NONE, 3, Menu.NONE, R.string.text_selection_more);

          return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
          return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
          if (!mIsSelectingText) {
            return true;
          }

          switch (item.getItemId()) {
            case 1:
              String selectedText = mEmulator.getSelectedText(mSelX1, mSelY1, mSelX2, mSelY2).trim();
              mTermSession.clipboardText(selectedText);
              break;
            case 2:
              pasteFromClipboard();
              break;
            case 3:
              showContextMenu();
              break;
          }
          toggleSelectingText(null);
          return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
      };

      mActionMode = startActionMode(new ActionMode.Callback2() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
          return callback.onCreateActionMode(mode, menu);
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
          return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
          return callback.onActionItemClicked(mode, item);
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }

        @Override
        public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
          int x1 = Math.round(mSelX1 * mRenderer.mFontWidth);
          int x2 = Math.round(mSelX2 * mRenderer.mFontWidth);
          int y1 = Math.round((mSelY1 - mTopRow) * mRenderer.mFontLineSpacing);
          int y2 = Math.round((mSelY2 + 1 - mTopRow) * mRenderer.mFontLineSpacing);
          outRect.set(Math.min(x1, x2), y1, Math.max(x1, x2), y2);
        }
      }, ActionMode.TYPE_FLOATING);
      invalidate();
    } else {
      mActionMode.finish();
      mSelX1 = mSelY1 = mSelX2 = mSelY2 = -1;
      invalidate();
    }
  }

  public TerminalSession getCurrentSession() {
    return mTermSession;
  }


  private OnAutoCompleteListener onAutoCompleteListener;

  public OnAutoCompleteListener getOnAutoCompleteListener() {
    return onAutoCompleteListener;
  }

  public void setOnAutoCompleteListener(OnAutoCompleteListener onAutoCompleteListener) {
    this.onAutoCompleteListener = onAutoCompleteListener;
  }

  public int getCursorAbsoluteX() {
    return (int) mRenderer.getCursorX();
  }

  public int getCursorAbsoluteY() {
    int[] locations = new int[2];
    getLocationOnScreen(locations);
    return (int) (mRenderer.getCursorY() + locations[1]);
  }

  public void setEnableWordBasedIme(boolean mEnableWordBasedIme) {
    this.mEnableWordBasedIme = mEnableWordBasedIme;
  }

  public void setOnTextSize(OnTextSize onTextSize) {
    this.onTextSize = onTextSize;
  }

}
