package com.stryker.terminal.backend;

import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;

public final class TerminalEmulator {

  private static final boolean LOG_ESCAPE_SEQUENCES = false;

  public static final int MOUSE_LEFT_BUTTON = 0;

  public static final int MOUSE_LEFT_BUTTON_MOVED = 32;
  public static final int MOUSE_WHEELUP_BUTTON = 64;
  public static final int MOUSE_WHEELDOWN_BUTTON = 65;

  public static final int CURSOR_STYLE_BLOCK = 0;
  public static final int CURSOR_STYLE_UNDERLINE = 1;
  public static final int CURSOR_STYLE_BAR = 2;

  public static final int UNICODE_REPLACEMENT_CHAR = 0xFFFD;

  private static final int ESC_NONE = 0;
  private static final int ESC = 1;
  private static final int ESC_POUND = 2;
  private static final int ESC_SELECT_LEFT_PAREN = 3;
  private static final int ESC_SELECT_RIGHT_PAREN = 4;
  private static final int ESC_CSI = 6;
  private static final int ESC_CSI_QUESTIONMARK = 7;
  private static final int ESC_CSI_DOLLAR = 8;
  private static final int ESC_PERCENT = 9;
  private static final int ESC_OSC = 10;
  private static final int ESC_OSC_ESC = 11;
  private static final int ESC_CSI_BIGGERTHAN = 12;
  private static final int ESC_P = 13;
  private static final int ESC_CSI_QUESTIONMARK_ARG_DOLLAR = 14;
  private static final int ESC_CSI_ARGS_SPACE = 15;
  private static final int ESC_CSI_ARGS_ASTERIX = 16;
  private static final int ESC_CSI_DOUBLE_QUOTE = 17;
  private static final int ESC_CSI_SINGLE_QUOTE = 18;
  private static final int ESC_CSI_EXCLAMATION = 19;

  private static final int MAX_ESCAPE_PARAMETERS = 16;

  private static final int MAX_OSC_STRING_LENGTH = 8192;

  private static final int DECSET_BIT_APPLICATION_CURSOR_KEYS = 1;
  private static final int DECSET_BIT_REVERSE_VIDEO = 1 << 1;
  private static final int DECSET_BIT_ORIGIN_MODE = 1 << 2;
  private static final int DECSET_BIT_AUTOWRAP = 1 << 3;
  private static final int DECSET_BIT_SHOWING_CURSOR = 1 << 4;
  private static final int DECSET_BIT_APPLICATION_KEYPAD = 1 << 5;
  private static final int DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE = 1 << 6;
  private static final int DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT = 1 << 7;
  private static final int DECSET_BIT_SEND_FOCUS_EVENTS = 1 << 8;
  private static final int DECSET_BIT_MOUSE_PROTOCOL_SGR = 1 << 9;
  private static final int DECSET_BIT_BRACKETED_PASTE_MODE = 1 << 10;
  private static final int DECSET_BIT_LEFTRIGHT_MARGIN_MODE = 1 << 11;
  private static final int DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE = 1 << 12;

  private String mTitle;
  private final Stack<String> mTitleStack = new Stack<>();

  private boolean mIsCSIStart;

  private Integer mLastCSIArg;

  private int mCursorRow, mCursorCol;

  private int mCursorStyle = CURSOR_STYLE_BLOCK;

  public int mRows, mColumns;

  private final TerminalBuffer mMainBuffer;
  final TerminalBuffer mAltBuffer;
  private TerminalBuffer mScreen;

  private final TerminalOutput mSession;

  private int mArgIndex;
  private final int[] mArgs = new int[MAX_ESCAPE_PARAMETERS];

  private final StringBuilder mOSCOrDeviceControlArgs = new StringBuilder();

  private boolean mContinueSequence;

  private int mEscapeState;

  private final SavedScreenState mSavedStateMain = new SavedScreenState();
  private final SavedScreenState mSavedStateAlt = new SavedScreenState();

  private boolean mUseLineDrawingG0, mUseLineDrawingG1, mUseLineDrawingUsesG0 = true;

  private int mCurrentDecSetFlags, mSavedDecSetFlags;

  private boolean mInsertMode;

  private boolean[] mTabStop;

  private int mTopMargin, mBottomMargin, mLeftMargin, mRightMargin;

  private boolean mAboutToAutoWrap;

  int mForeColor, mBackColor;

  private int mEffect;

  private int mScrollCounter = 0;

  private byte mUtf8ToFollow, mUtf8Index;
  private final byte[] mUtf8InputBuffer = new byte[4];
  private int mLastEmittedCodePoint = -1;

  public final TerminalColors mColors = new TerminalColors();

  private boolean isDecsetInternalBitSet(int bit) {
    return (mCurrentDecSetFlags & bit) != 0;
  }

  private void setDecsetinternalBit(int internalBit, boolean set) {
    if (set) {
      if (internalBit == DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) {
        setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT, false);
      } else if (internalBit == DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT) {
        setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE, false);
      }
    }
    if (set) {
      mCurrentDecSetFlags |= internalBit;
    } else {
      mCurrentDecSetFlags &= ~internalBit;
    }
  }

  static int mapDecSetBitToInternalBit(int decsetBit) {
    switch (decsetBit) {
      case 1:
        return DECSET_BIT_APPLICATION_CURSOR_KEYS;
      case 5:
        return DECSET_BIT_REVERSE_VIDEO;
      case 6:
        return DECSET_BIT_ORIGIN_MODE;
      case 7:
        return DECSET_BIT_AUTOWRAP;
      case 25:
        return DECSET_BIT_SHOWING_CURSOR;
      case 66:
        return DECSET_BIT_APPLICATION_KEYPAD;
      case 69:
        return DECSET_BIT_LEFTRIGHT_MARGIN_MODE;
      case 1000:
        return DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE;
      case 1002:
        return DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT;
      case 1004:
        return DECSET_BIT_SEND_FOCUS_EVENTS;
      case 1006:
        return DECSET_BIT_MOUSE_PROTOCOL_SGR;
      case 2004:
        return DECSET_BIT_BRACKETED_PASTE_MODE;
      default:
        return -1;
    }
  }

  public TerminalEmulator(TerminalOutput session, int columns, int rows, int transcriptRows) {
    mSession = session;
    mScreen = mMainBuffer = new TerminalBuffer(columns, transcriptRows, rows);
    mAltBuffer = new TerminalBuffer(columns, rows, rows);
    mRows = rows;
    mColumns = columns;
    mTabStop = new boolean[mColumns];
    reset();
  }

  public TerminalBuffer getScreen() {
    return mScreen;
  }

  public boolean isAlternateBufferActive() {
    return mScreen == mAltBuffer;
  }

  public void sendMouseEvent(int mouseButton, int column, int row, boolean pressed) {
    if (column < 1) column = 1;
    if (column > mColumns) column = mColumns;
    if (row < 1) row = 1;
    if (row > mRows) row = mRows;

    if (mouseButton == MOUSE_LEFT_BUTTON_MOVED && !isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT)) {
    } else if (isDecsetInternalBitSet(DECSET_BIT_MOUSE_PROTOCOL_SGR)) {
      mSession.write(String.format("\033[<%d;%d;%d" + (pressed ? 'M' : 'm'), mouseButton, column, row));
    } else {
      mouseButton = pressed ? mouseButton : 3;
      boolean outOfBounds = column > 255 - 32 || row > 255 - 32;
      if (!outOfBounds) {
        byte[] data = {'\033', '[', 'M', (byte) (32 + mouseButton), (byte) (32 + column), (byte) (32 + row)};
        mSession.write(data, 0, data.length);
      }
    }
  }

  public void resize(int columns, int rows) {
    if (mRows == rows && mColumns == columns) {
      return;
    } else if (columns < 2 || rows < 2) {
      throw new IllegalArgumentException("rows=" + rows + ", columns=" + columns);
    }

    if (mRows != rows) {
      mRows = rows;
      mTopMargin = 0;
      mBottomMargin = mRows;
    }
    if (mColumns != columns) {
      int oldColumns = mColumns;
      mColumns = columns;
      boolean[] oldTabStop = mTabStop;
      mTabStop = new boolean[mColumns];
      setDefaultTabStops();
      int toTransfer = Math.min(oldColumns, columns);
      System.arraycopy(oldTabStop, 0, mTabStop, 0, toTransfer);
      mLeftMargin = 0;
      mRightMargin = mColumns;
    }

    resizeScreen();
  }

  private void resizeScreen() {
    final int[] cursor = {mCursorCol, mCursorRow};
    int newTotalRows = (mScreen == mAltBuffer) ? mRows : mMainBuffer.mTotalRows;
    mScreen.resize(mColumns, mRows, newTotalRows, cursor, getStyle(), isAlternateBufferActive());
    mCursorCol = cursor[0];
    mCursorRow = cursor[1];
  }

  public int getCursorRow() {
    return mCursorRow;
  }

  public int getCursorCol() {
    return mCursorCol;
  }

  public int getCursorStyle() {
    return mCursorStyle;
  }

  public boolean isReverseVideo() {
    return isDecsetInternalBitSet(DECSET_BIT_REVERSE_VIDEO);
  }

  public boolean isShowingCursor() {
    return isDecsetInternalBitSet(DECSET_BIT_SHOWING_CURSOR);
  }

  public boolean isKeypadApplicationMode() {
    return isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD);
  }

  public boolean isCursorKeysApplicationMode() {
    return isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS);
  }

  public boolean isMouseTrackingActive() {
    return isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) || isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT);
  }

  private void setDefaultTabStops() {
    for (int i = 0; i < mColumns; i++)
      mTabStop[i] = (i & 7) == 0 && i != 0;
  }

  public void append(byte[] buffer, int length) {
    for (int i = 0; i < length; i++)
      processByte(buffer[i]);
  }

  private void processByte(byte byteToProcess) {
    if (mUtf8ToFollow > 0) {
      if ((byteToProcess & 0b11000000) == 0b10000000) {
        mUtf8InputBuffer[mUtf8Index++] = byteToProcess;
        if (--mUtf8ToFollow == 0) {
          byte firstByteMask = (byte) (mUtf8Index == 2 ? 0b00011111 : (mUtf8Index == 3 ? 0b00001111 : 0b00000111));
          int codePoint = (mUtf8InputBuffer[0] & firstByteMask);
          for (int i = 1; i < mUtf8Index; i++)
            codePoint = ((codePoint << 6) | (mUtf8InputBuffer[i] & 0b00111111));
          if (((codePoint <= 0b1111111) && mUtf8Index > 1) || (codePoint < 0b11111111111 && mUtf8Index > 2)
            || (codePoint < 0b1111111111111111 && mUtf8Index > 3)) {
            codePoint = UNICODE_REPLACEMENT_CHAR;
          }

          mUtf8Index = mUtf8ToFollow = 0;

          if (codePoint >= 0x80 && codePoint <= 0x9F) {
          } else {
            switch (Character.getType(codePoint)) {
              case Character.UNASSIGNED:
              case Character.SURROGATE:
                codePoint = UNICODE_REPLACEMENT_CHAR;
            }
            processCodePoint(codePoint);
          }
        }
      } else {
        mUtf8Index = mUtf8ToFollow = 0;
        emitCodePoint(UNICODE_REPLACEMENT_CHAR);
        processByte(byteToProcess);
      }
    } else {
      if ((byteToProcess & 0b10000000) == 0) {
        processCodePoint(byteToProcess);
        return;
      } else if ((byteToProcess & 0b11100000) == 0b11000000) {
        mUtf8ToFollow = 1;
      } else if ((byteToProcess & 0b11110000) == 0b11100000) {
        mUtf8ToFollow = 2;
      } else if ((byteToProcess & 0b11111000) == 0b11110000) {
        mUtf8ToFollow = 3;
      } else {
        processCodePoint(UNICODE_REPLACEMENT_CHAR);
        return;
      }
      mUtf8InputBuffer[mUtf8Index++] = byteToProcess;
    }
  }

  public void processCodePoint(int b) {
    switch (b) {
      case 0:
        break;
      case 7:
        if (mEscapeState == ESC_OSC)
          doOsc(b);
        else
          mSession.onBell();
        break;
      case 8:
        if (mLeftMargin == mCursorCol) {
          int previousRow = mCursorRow - 1;
          if (previousRow >= 0 && mScreen.getLineWrap(previousRow)) {
            mScreen.clearLineWrap(previousRow);
            setCursorRowCol(previousRow, mRightMargin - 1);
          }
        } else {
          setCursorCol(mCursorCol - 1);
        }
        break;
      case 9:
        mCursorCol = nextTabStop(1);
        break;
      case 10:
      case 11:
      case 12:
        doLinefeed();
        break;
      case 13:
        setCursorCol(mLeftMargin);
        break;
      case 14:
        mUseLineDrawingUsesG0 = false;
        break;
      case 15:
        mUseLineDrawingUsesG0 = true;
        break;
      case 24:
      case 26:
        if (mEscapeState != ESC_NONE) {
          mEscapeState = ESC_NONE;
          emitCodePoint(127);
        }
        break;
      case 27:
        if (mEscapeState == ESC_P) {
          return;
        } else if (mEscapeState != ESC_OSC) {
          startEscapeSequence();
        } else {
          doOsc(b);
        }
        break;
      default:
        mContinueSequence = false;
        switch (mEscapeState) {
          case ESC_NONE:
            if (b >= 32) emitCodePoint(b);
            break;
          case ESC:
            doEsc(b);
            break;
          case ESC_POUND:
            doEscPound(b);
            break;
          case ESC_SELECT_LEFT_PAREN:
            mUseLineDrawingG0 = (b == '0');
            break;
          case ESC_SELECT_RIGHT_PAREN:
            mUseLineDrawingG1 = (b == '0');
            break;
          case ESC_CSI:
            doCsi(b);
            break;
          case ESC_CSI_EXCLAMATION:
            if (b == 'p') {
              reset();
            } else {
              unknownSequence(b);
            }
            break;
          case ESC_CSI_QUESTIONMARK:
            doCsiQuestionMark(b);
            break;
          case ESC_CSI_BIGGERTHAN:
            doCsiBiggerThan(b);
            break;
          case ESC_CSI_DOLLAR:
            boolean originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE);
            int effectiveTopMargin = originMode ? mTopMargin : 0;
            int effectiveBottomMargin = originMode ? mBottomMargin : mRows;
            int effectiveLeftMargin = originMode ? mLeftMargin : 0;
            int effectiveRightMargin = originMode ? mRightMargin : mColumns;
            switch (b) {
              case 'v':
                int topSource = Math.min(getArg(0, 1, true) - 1 + effectiveTopMargin, mRows);
                int leftSource = Math.min(getArg(1, 1, true) - 1 + effectiveLeftMargin, mColumns);
                int bottomSource = Math.min(Math.max(getArg(2, mRows, true) + effectiveTopMargin, topSource), mRows);
                int rightSource = Math.min(Math.max(getArg(3, mColumns, true) + effectiveLeftMargin, leftSource), mColumns);
                int destionationTop = Math.min(getArg(5, 1, true) - 1 + effectiveTopMargin, mRows);
                int destinationLeft = Math.min(getArg(6, 1, true) - 1 + effectiveLeftMargin, mColumns);
                int heightToCopy = Math.min(mRows - destionationTop, bottomSource - topSource);
                int widthToCopy = Math.min(mColumns - destinationLeft, rightSource - leftSource);
                mScreen.blockCopy(leftSource, topSource, widthToCopy, heightToCopy, destinationLeft, destionationTop);
                break;
              case '{':
              case 'x':
              case 'z':
                boolean erase = b != 'x';
                boolean selective = b == '{';
                boolean keepVisualAttributes = erase && selective;
                int argIndex = 0;
                int fillChar = erase ? ' ' : getArg(argIndex++, -1, true);
                if ((fillChar >= 32 && fillChar <= 126) || (fillChar >= 160 && fillChar <= 255)) {
                  int top = Math.min(getArg(argIndex++, 1, true) + effectiveTopMargin, effectiveBottomMargin + 1);
                  int left = Math.min(getArg(argIndex++, 1, true) + effectiveLeftMargin, effectiveRightMargin + 1);
                  int bottom = Math.min(getArg(argIndex++, mRows, true) + effectiveTopMargin, effectiveBottomMargin);
                  int right = Math.min(getArg(argIndex, mColumns, true) + effectiveLeftMargin, effectiveRightMargin);
                  long style = getStyle();
                  for (int row = top - 1; row < bottom; row++)
                    for (int col = left - 1; col < right; col++)
                      if (!selective || (TextStyle.decodeEffect(mScreen.getStyleAt(row, col)) & TextStyle.CHARACTER_ATTRIBUTE_PROTECTED) == 0)
                        mScreen.setChar(col, row, fillChar, keepVisualAttributes ? mScreen.getStyleAt(row, col) : style);
                }
                break;
              case 'r':
              case 't':
                boolean reverse = b == 't';
                int top = Math.min(getArg(0, 1, true) - 1, effectiveBottomMargin) + effectiveTopMargin;
                int left = Math.min(getArg(1, 1, true) - 1, effectiveRightMargin) + effectiveLeftMargin;
                int bottom = Math.min(getArg(2, mRows, true) + 1, effectiveBottomMargin - 1) + effectiveTopMargin;
                int right = Math.min(getArg(3, mColumns, true) + 1, effectiveRightMargin - 1) + effectiveLeftMargin;
                if (mArgIndex >= 4) {
                  if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
                  for (int i = 4; i <= mArgIndex; i++) {
                    int bits = 0;
                    boolean setOrClear = true;
                    switch (getArg(i, 0, false)) {
                      case 0:
                        bits = (TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE | TextStyle.CHARACTER_ATTRIBUTE_BLINK
                          | TextStyle.CHARACTER_ATTRIBUTE_INVERSE);
                        if (!reverse) setOrClear = false;
                        break;
                      case 1:
                        bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD;
                        break;
                      case 4:
                        bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                        break;
                      case 5:
                        bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK;
                        break;
                      case 7:
                        bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                        break;
                      case 22:
                        bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD;
                        setOrClear = false;
                        break;
                      case 24:
                        bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
                        setOrClear = false;
                        break;
                      case 25:
                        bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK;
                        setOrClear = false;
                        break;
                      case 27:
                        bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
                        setOrClear = false;
                        break;
                    }
                    if (reverse && !setOrClear) {
                    } else {
                      mScreen.setOrClearEffect(bits, setOrClear, reverse, isDecsetInternalBitSet(DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE),
                        effectiveLeftMargin, effectiveRightMargin, top, left, bottom, right);
                    }
                  }
                } else {
                }
                break;
              default:
                unknownSequence(b);
            }
            break;
          case ESC_CSI_DOUBLE_QUOTE:
            if (b == 'q') {
              int arg = getArg0(0);
              if (arg == 0 || arg == 2) {
                mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_PROTECTED;
              } else if (arg == 1) {
                mEffect |= TextStyle.CHARACTER_ATTRIBUTE_PROTECTED;
              } else {
                unknownSequence(b);
              }
            } else {
              unknownSequence(b);
            }
            break;
          case ESC_CSI_SINGLE_QUOTE:
            if (b == '}') {
              int columnsAfterCursor = mRightMargin - mCursorCol;
              int columnsToInsert = Math.min(getArg0(1), columnsAfterCursor);
              int columnsToMove = columnsAfterCursor - columnsToInsert;
              mScreen.blockCopy(mCursorCol, 0, columnsToMove, mRows, mCursorCol + columnsToInsert, 0);
              blockClear(mCursorCol, 0, columnsToInsert, mRows);
            } else if (b == '~') {
              int columnsAfterCursor = mRightMargin - mCursorCol;
              int columnsToDelete = Math.min(getArg0(1), columnsAfterCursor);
              int columnsToMove = columnsAfterCursor - columnsToDelete;
              mScreen.blockCopy(mCursorCol + columnsToDelete, 0, columnsToMove, mRows, mCursorCol, 0);
              blockClear(mCursorRow + columnsToMove, 0, columnsToDelete, mRows);
            } else {
              unknownSequence(b);
            }
            break;
          case ESC_PERCENT:
            break;
          case ESC_OSC:
            doOsc(b);
            break;
          case ESC_OSC_ESC:
            doOscEsc(b);
            break;
          case ESC_P:
            doDeviceControl(b);
            break;
          case ESC_CSI_QUESTIONMARK_ARG_DOLLAR:
            if (b == 'p') {
              int mode = getArg0(0);
              int value;
              if (mode == 47 || mode == 1047 || mode == 1049) {
                value = (mScreen == mAltBuffer) ? 1 : 2;
              } else {
                int internalBit = mapDecSetBitToInternalBit(mode);
                if (internalBit == -1) {
                  value = isDecsetInternalBitSet(internalBit) ? 1 : 2;
                } else {
                  Log.e(EmulatorDebug.LOG_TAG, "Got DECRQM for unrecognized private DEC mode=" + mode);
                  value = 0;
                }
              }
              mSession.write(String.format(Locale.US, "\033[?%d;%d$y", mode, value));
            } else {
              unknownSequence(b);
            }
            break;
          case ESC_CSI_ARGS_SPACE:
            int arg = getArg0(0);
            switch (b) {
              case 'q':
                switch (arg) {
                  case 0:
                  case 1:
                  case 2:
                    mCursorStyle = CURSOR_STYLE_BLOCK;
                    break;
                  case 3:
                  case 4:
                    mCursorStyle = CURSOR_STYLE_UNDERLINE;
                    break;
                  case 5:
                  case 6:
                    mCursorStyle = CURSOR_STYLE_BAR;
                    break;
                }
                break;
              case 't':
              case 'u':
                break;
              default:
                unknownSequence(b);
            }
            break;
          case ESC_CSI_ARGS_ASTERIX:
            int attributeChangeExtent = getArg0(0);
            if (b == 'x' && (attributeChangeExtent >= 0 && attributeChangeExtent <= 2)) {
              setDecsetinternalBit(DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE, attributeChangeExtent == 2);
            } else {
              unknownSequence(b);
            }
            break;
          default:
            unknownSequence(b);
            break;
        }
        if (!mContinueSequence) mEscapeState = ESC_NONE;
        break;
    }
  }

  private void doDeviceControl(int b) {
    switch (b) {
      case (byte) '\\':
      {
        String dcs = mOSCOrDeviceControlArgs.toString();
        if (dcs.startsWith("$q")) {
          if (dcs.equals("$q\"p")) {
            String csiString = "64;1\"p";
            mSession.write("\033P1$r" + csiString + "\033\\");
          } else {
            finishSequenceAndLogError("Unrecognized DECRQSS string: '" + dcs + "'");
          }
        } else if (dcs.startsWith("+q")) {
          for (String part : dcs.substring(2).split(";")) {
            if (part.length() % 2 == 0) {
              StringBuilder transBuffer = new StringBuilder();
              for (int i = 0; i < part.length(); i += 2) {
                char c = (char) Long.decode("0x" + part.charAt(i) + "" + part.charAt(i + 1)).longValue();
                transBuffer.append(c);
              }
              String trans = transBuffer.toString();
              String responseValue;
              switch (trans) {
                case "Co":
                case "colors":
                  responseValue = "256";
                  break;
                case "TN":
                case "name":
                  responseValue = "xterm";
                  break;
                default:
                  responseValue = KeyHandler.getCodeFromTermcap(trans, isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS),
                    isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD));
                  break;
              }
              if (responseValue == null) {
                switch (trans) {
                  case "%1":
                  case "&8":
                    break;
                  default:
                    Log.w(EmulatorDebug.LOG_TAG, "Unhandled termcap/terminfo name: '" + trans + "'");
                }
                mSession.write("\033P0+r" + part + "\033\\");
              } else {
                StringBuilder hexEncoded = new StringBuilder();
                for (int j = 0; j < responseValue.length(); j++) {
                  hexEncoded.append(String.format("%02X", (int) responseValue.charAt(j)));
                }
                mSession.write("\033P1+r" + part + "=" + hexEncoded + "\033\\");
              }
            } else {
              Log.e(EmulatorDebug.LOG_TAG, "Invalid device termcap/terminfo name of odd length: " + part);
            }
          }
        } else {
          if (LOG_ESCAPE_SEQUENCES)
            Log.e(EmulatorDebug.LOG_TAG, "Unrecognized device control string: " + dcs);
        }
        finishSequence();
      }
      break;
      default:
        if (mOSCOrDeviceControlArgs.length() > MAX_OSC_STRING_LENGTH) {
          mOSCOrDeviceControlArgs.setLength(0);
          finishSequence();
        } else {
          mOSCOrDeviceControlArgs.appendCodePoint(b);
          continueSequence(mEscapeState);
        }
    }
  }

  private int nextTabStop(int numTabs) {
    for (int i = mCursorCol + 1; i < mColumns; i++)
      if (mTabStop[i] && --numTabs == 0) return Math.min(i, mRightMargin);
    return mRightMargin - 1;
  }

  private void doCsiQuestionMark(int b) {
    switch (b) {
      case 'J':
      case 'K':
        mAboutToAutoWrap = false;
        int fillChar = ' ';
        int startCol = -1;
        int startRow = -1;
        int endCol = -1;
        int endRow = -1;
        boolean justRow = (b == 'K');
        switch (getArg0(0)) {
          case 0:
            startCol = mCursorCol;
            startRow = mCursorRow;
            endCol = mColumns;
            endRow = justRow ? (mCursorRow + 1) : mRows;
            break;
          case 1:
            startCol = 0;
            startRow = justRow ? mCursorRow : 0;
            endCol = mCursorCol + 1;
            endRow = mCursorRow + 1;
            break;
          case 2:
            startCol = 0;
            startRow = justRow ? mCursorRow : 0;
            endCol = mColumns;
            endRow = justRow ? (mCursorRow + 1) : mRows;
            break;
          default:
            unknownSequence(b);
            break;
        }
        long style = getStyle();
        for (int row = startRow; row < endRow; row++) {
          for (int col = startCol; col < endCol; col++) {
            if ((TextStyle.decodeEffect(mScreen.getStyleAt(row, col)) & TextStyle.CHARACTER_ATTRIBUTE_PROTECTED) == 0)
              mScreen.setChar(col, row, fillChar, style);
          }
        }
        break;
      case 'h':
      case 'l':
        if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
        for (int i = 0; i <= mArgIndex; i++)
          doDecSetOrReset(b == 'h', mArgs[i]);
        break;
      case 'n':
        switch (getArg0(-1)) {
          case 6:
            mSession.write(String.format(Locale.US, "\033[?%d;%d;1R", mCursorRow + 1, mCursorCol + 1));
            break;
          default:
            finishSequence();
            return;
        }
        break;
      case 'r':
      case 's':
        if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
        for (int i = 0; i <= mArgIndex; i++) {
          int externalBit = mArgs[i];
          int internalBit = mapDecSetBitToInternalBit(externalBit);
          if (internalBit == -1) {
            Log.w(EmulatorDebug.LOG_TAG, "Ignoring request to save/recall decset bit=" + externalBit);
          } else {
            if (b == 's') {
              mSavedDecSetFlags |= internalBit;
            } else {
              doDecSetOrReset((mSavedDecSetFlags & internalBit) != 0, externalBit);
            }
          }
        }
        break;
      case '$':
        continueSequence(ESC_CSI_QUESTIONMARK_ARG_DOLLAR);
        return;
      default:
        parseArg(b);
    }
  }

  public void doDecSetOrReset(boolean setting, int externalBit) {
    int internalBit = mapDecSetBitToInternalBit(externalBit);
    if (internalBit != -1) {
      setDecsetinternalBit(internalBit, setting);
    }
    switch (externalBit) {
      case 1:
        break;
      case 3:
        mLeftMargin = mTopMargin = 0;
        mBottomMargin = mRows;
        mRightMargin = mColumns;
        setDecsetinternalBit(DECSET_BIT_LEFTRIGHT_MARGIN_MODE, false);
        blockClear(0, 0, mColumns, mRows);
        setCursorRowCol(0, 0);
        break;
      case 4:
        break;
      case 5:
        break;
      case 6:
        if (setting) setCursorPosition(0, 0);
        break;
      case 7:
      case 8:
      case 9:
      case 12:
      case 25:
      case 40:
      case 45:
      case 66:
        break;
      case 69:
        if (!setting) {
          mLeftMargin = 0;
          mRightMargin = mColumns;
        }
        break;
      case 1000:
      case 1001:
      case 1002:
      case 1003:
      case 1004:
      case 1005:
      case 1006:
      case 1015:
      case 1034:
        break;
      case 1048:
        if (setting)
          saveCursor();
        else
          restoreCursor();
        break;
      case 47:
      case 1047:
      case 1049: {
        TerminalBuffer newScreen = setting ? mAltBuffer : mMainBuffer;
        if (newScreen != mScreen) {
          boolean resized = !(newScreen.mColumns == mColumns && newScreen.mScreenRows == mRows);
          if (setting) saveCursor();
          mScreen = newScreen;
          if (!setting) {
            int col = mSavedStateMain.mSavedCursorCol;
            int row = mSavedStateMain.mSavedCursorRow;
            restoreCursor();
            if (resized) {
              mCursorCol = col;
              mCursorRow = row;
            }
          }
          if (resized) resizeScreen();
          if (newScreen == mAltBuffer)
            newScreen.blockSet(0, 0, mColumns, mRows, ' ', getStyle());
        }
        break;
      }
      case 2004:
        break;
      default:
        unknownParameter(externalBit);
        break;
    }
  }

  private void doCsiBiggerThan(int b) {
    switch (b) {
      case 'c':
        mSession.write("\033[>41;320;0c");
        break;
      case 'm':
        Log.e(EmulatorDebug.LOG_TAG, "(ignored) CSI > MODIFY RESOURCE: " + getArg0(-1) + " to " + getArg1(-1));
        break;
      default:
        parseArg(b);
        break;
    }
  }

  private void startEscapeSequence() {
    mEscapeState = ESC;
    mArgIndex = 0;
    Arrays.fill(mArgs, -1);
  }

  private void doLinefeed() {
    boolean belowScrollingRegion = mCursorRow >= mBottomMargin;
    int newCursorRow = mCursorRow + 1;
    if (belowScrollingRegion) {
      if (mCursorRow != mRows - 1) {
        setCursorRow(newCursorRow);
      }
    } else {
      if (newCursorRow == mBottomMargin) {
        scrollDownOneLine();
        newCursorRow = mBottomMargin - 1;
      }
      setCursorRow(newCursorRow);
    }
  }

  private void continueSequence(int state) {
    mEscapeState = state;
    mContinueSequence = true;
  }

  private void doEscPound(int b) {
    switch (b) {
      case '8':
        mScreen.blockSet(0, 0, mColumns, mRows, 'E', getStyle());
        break;
      default:
        unknownSequence(b);
        break;
    }
  }

  private void doEsc(int b) {
    switch (b) {
      case '#':
        continueSequence(ESC_POUND);
        break;
      case '(':
        continueSequence(ESC_SELECT_LEFT_PAREN);
        break;
      case ')':
        continueSequence(ESC_SELECT_RIGHT_PAREN);
        break;
      case '6':
        if (mCursorCol > mLeftMargin) {
          mCursorCol--;
        } else {
          int rows = mBottomMargin - mTopMargin;
          mScreen.blockCopy(mLeftMargin, mTopMargin, mRightMargin - mLeftMargin - 1, rows, mLeftMargin + 1, mTopMargin);
          mScreen.blockSet(mLeftMargin, mTopMargin, 1, rows, ' ', TextStyle.encode(mForeColor, mBackColor, 0));
        }
        break;
      case '7':
        saveCursor();
        break;
      case '8':
        restoreCursor();
        break;
      case '9':
        if (mCursorCol < mRightMargin - 1) {
          mCursorCol++;
        } else {
          int rows = mBottomMargin - mTopMargin;
          mScreen.blockCopy(mLeftMargin + 1, mTopMargin, mRightMargin - mLeftMargin - 1, rows, mLeftMargin, mTopMargin);
          mScreen.blockSet(mRightMargin - 1, mTopMargin, 1, rows, ' ', TextStyle.encode(mForeColor, mBackColor, 0));
        }
        break;
      case 'c':
        reset();
        mMainBuffer.clearTranscript();
        blockClear(0, 0, mColumns, mRows);
        setCursorPosition(0, 0);
        break;
      case 'D':
        doLinefeed();
        break;
      case 'E':
        setCursorCol(isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE) ? mLeftMargin : 0);
        doLinefeed();
        break;
      case 'F':
        setCursorRowCol(0, mBottomMargin - 1);
        break;
      case 'H':
        mTabStop[mCursorCol] = true;
        break;
      case 'M':
        if (mCursorRow <= mTopMargin) {
          mScreen.blockCopy(0, mTopMargin, mColumns, mBottomMargin - (mTopMargin + 1), 0, mTopMargin + 1);
          blockClear(0, mTopMargin, mColumns);
        } else {
          mCursorRow--;
        }
        break;
      case 'N':
      case '0':
        break;
      case 'P':
        mOSCOrDeviceControlArgs.setLength(0);
        continueSequence(ESC_P);
        break;
      case '[':
        continueSequence(ESC_CSI);
        mIsCSIStart = true;
        mLastCSIArg = null;
        break;
      case '=':
        setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, true);
        break;
      case ']':
        mOSCOrDeviceControlArgs.setLength(0);
        continueSequence(ESC_OSC);
        break;
      case '>':
        setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, false);
        break;
      default:
        unknownSequence(b);
        break;
    }
  }

  private void saveCursor() {
    SavedScreenState state = (mScreen == mMainBuffer) ? mSavedStateMain : mSavedStateAlt;
    state.mSavedCursorRow = mCursorRow;
    state.mSavedCursorCol = mCursorCol;
    state.mSavedEffect = mEffect;
    state.mSavedForeColor = mForeColor;
    state.mSavedBackColor = mBackColor;
    state.mSavedDecFlags = mCurrentDecSetFlags;
    state.mUseLineDrawingG0 = mUseLineDrawingG0;
    state.mUseLineDrawingG1 = mUseLineDrawingG1;
    state.mUseLineDrawingUsesG0 = mUseLineDrawingUsesG0;
  }

  private void restoreCursor() {
    SavedScreenState state = (mScreen == mMainBuffer) ? mSavedStateMain : mSavedStateAlt;
    setCursorRowCol(state.mSavedCursorRow, state.mSavedCursorCol);
    mEffect = state.mSavedEffect;
    mForeColor = state.mSavedForeColor;
    mBackColor = state.mSavedBackColor;
    int mask = (DECSET_BIT_AUTOWRAP | DECSET_BIT_ORIGIN_MODE);
    mCurrentDecSetFlags = (mCurrentDecSetFlags & ~mask) | (state.mSavedDecFlags & mask);
    mUseLineDrawingG0 = state.mUseLineDrawingG0;
    mUseLineDrawingG1 = state.mUseLineDrawingG1;
    mUseLineDrawingUsesG0 = state.mUseLineDrawingUsesG0;
  }

  private void doCsi(int b) {
    switch (b) {
      case '!':
        continueSequence(ESC_CSI_EXCLAMATION);
        break;
      case '"':
        continueSequence(ESC_CSI_DOUBLE_QUOTE);
        break;
      case '\'':
        continueSequence(ESC_CSI_SINGLE_QUOTE);
        break;
      case '$':
        continueSequence(ESC_CSI_DOLLAR);
        break;
      case '*':
        continueSequence(ESC_CSI_ARGS_ASTERIX);
        break;
      case '@': {
        mAboutToAutoWrap = false;
        int columnsAfterCursor = mColumns - mCursorCol;
        int spacesToInsert = Math.min(getArg0(1), columnsAfterCursor);
        int charsToMove = columnsAfterCursor - spacesToInsert;
        mScreen.blockCopy(mCursorCol, mCursorRow, charsToMove, 1, mCursorCol + spacesToInsert, mCursorRow);
        blockClear(mCursorCol, mCursorRow, spacesToInsert);
      }
      break;
      case 'A':
        setCursorRow(Math.max(mTopMargin, mCursorRow - getArg0(1)));
        break;
      case 'B':
        setCursorRow(Math.min(mBottomMargin - 1, mCursorRow + getArg0(1)));
        break;
      case 'C':
      case 'a':
        setCursorCol(Math.min(mRightMargin - 1, mCursorCol + getArg0(1)));
        break;
      case 'D':
        setCursorCol(Math.max(mLeftMargin, mCursorCol - getArg0(1)));
        break;
      case 'E':
        setCursorPosition(0, mCursorRow + getArg0(1));
        break;
      case 'F':
        setCursorPosition(0, mCursorRow - getArg0(1));
        break;
      case 'G':
        setCursorCol(Math.min(Math.max(1, getArg0(1)), mColumns) - 1);
        break;
      case 'H':
      case 'f':
        setCursorPosition(getArg1(1) - 1, getArg0(1) - 1);
        break;
      case 'I':
        setCursorCol(nextTabStop(getArg0(1)));
        break;
      case 'J':
        switch (getArg0(0)) {
          case 0:
            blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol);
            blockClear(0, mCursorRow + 1, mColumns, mRows - (mCursorRow + 1));
            break;
          case 1:
            blockClear(0, 0, mColumns, mCursorRow);
            blockClear(0, mCursorRow, mCursorCol + 1);
            break;
          case 2:
            blockClear(0, 0, mColumns, mRows);
            break;
          case 3:
            mMainBuffer.clearTranscript();
            break;
          default:
            unknownSequence(b);
            return;
        }
        mAboutToAutoWrap = false;
        break;
      case 'K':
        switch (getArg0(0)) {
          case 0:
            blockClear(mCursorCol, mCursorRow, mColumns - mCursorCol);
            break;
          case 1:
            blockClear(0, mCursorRow, mCursorCol + 1);
            break;
          case 2:
            blockClear(0, mCursorRow, mColumns);
            break;
          default:
            unknownSequence(b);
            return;
        }
        mAboutToAutoWrap = false;
        break;
      case 'L':
      {
        int linesAfterCursor = mBottomMargin - mCursorRow;
        int linesToInsert = Math.min(getArg0(1), linesAfterCursor);
        int linesToMove = linesAfterCursor - linesToInsert;
        mScreen.blockCopy(0, mCursorRow, mColumns, linesToMove, 0, mCursorRow + linesToInsert);
        blockClear(0, mCursorRow, mColumns, linesToInsert);
      }
      break;
      case 'M':
      {
        mAboutToAutoWrap = false;
        int linesAfterCursor = mBottomMargin - mCursorRow;
        int linesToDelete = Math.min(getArg0(1), linesAfterCursor);
        int linesToMove = linesAfterCursor - linesToDelete;
        mScreen.blockCopy(0, mCursorRow + linesToDelete, mColumns, linesToMove, 0, mCursorRow);
        blockClear(0, mCursorRow + linesToMove, mColumns, linesToDelete);
      }
      break;
      case 'P':
      {
        mAboutToAutoWrap = false;
        int cellsAfterCursor = mColumns - mCursorCol;
        int cellsToDelete = Math.min(getArg0(1), cellsAfterCursor);
        int cellsToMove = cellsAfterCursor - cellsToDelete;
        mScreen.blockCopy(mCursorCol + cellsToDelete, mCursorRow, cellsToMove, 1, mCursorCol, mCursorRow);
        blockClear(mCursorCol + cellsToMove, mCursorRow, cellsToDelete);
      }
      break;
      case 'S': {
        final int linesToScroll = getArg0(1);
        for (int i = 0; i < linesToScroll; i++)
          scrollDownOneLine();
        break;
      }
      case 'T':
        if (mArgIndex == 0) {
          final int linesToScrollArg = getArg0(1);
          final int linesBetweenTopAndBottomMargins = mBottomMargin - mTopMargin;
          final int linesToScroll = Math.min(linesBetweenTopAndBottomMargins, linesToScrollArg);
          mScreen.blockCopy(0, mTopMargin, mColumns, linesBetweenTopAndBottomMargins - linesToScroll, 0, mTopMargin + linesToScroll);
          blockClear(0, mTopMargin, mColumns, linesToScroll);
        } else {
          unimplementedSequence(b);
        }
        break;
      case 'X':
        mAboutToAutoWrap = false;
        mScreen.blockSet(mCursorCol, mCursorRow, Math.min(getArg0(1), mColumns - mCursorCol), 1, ' ', getStyle());
        break;
      case 'Z':
        int numberOfTabs = getArg0(1);
        int newCol = mLeftMargin;
        for (int i = mCursorCol - 1; i >= 0; i--)
          if (mTabStop[i]) {
            if (--numberOfTabs == 0) {
              newCol = Math.max(i, mLeftMargin);
              break;
            }
          }
        mCursorCol = newCol;
        break;
      case '?':
        continueSequence(ESC_CSI_QUESTIONMARK);
        break;
      case '>':
        continueSequence(ESC_CSI_BIGGERTHAN);
        break;
      case '`':
        setCursorColRespectingOriginMode(getArg0(1) - 1);
        break;
      case 'b':
        if (mLastEmittedCodePoint == -1) break;
        final int numRepeat = getArg0(1);
        for (int i = 0; i < numRepeat; i++) emitCodePoint(mLastEmittedCodePoint);
        break;
      case 'c':
        if (getArg0(0) == 0) mSession.write("\033[?64;1;2;6;9;15;18;21;22c");
        break;
      case 'd':
        setCursorRow(Math.min(Math.max(1, getArg0(1)), mRows) - 1);
        break;
      case 'e':
        setCursorPosition(mCursorCol, mCursorRow + getArg0(1));
        break;
      case 'g':
        switch (getArg0(0)) {
          case 0:
            mTabStop[mCursorCol] = false;
            break;
          case 3:
            for (int i = 0; i < mColumns; i++) {
              mTabStop[i] = false;
            }
            break;
          default:
            break;
        }
        break;
      case 'h':
        doSetMode(true);
        break;
      case 'l':
        doSetMode(false);
        break;
      case 'm':
        selectGraphicRendition();
        break;
      case 'n':
        switch (getArg0(0)) {
          case 5:
            byte[] dsr = {(byte) 27, (byte) '[', (byte) '0', (byte) 'n'};
            mSession.write(dsr, 0, dsr.length);
            break;
          case 6:
            mSession.write(String.format(Locale.US, "\033[%d;%dR", mCursorRow + 1, mCursorCol + 1));
            break;
          default:
            break;
        }
        break;
      case 'r':
      {
        mTopMargin = Math.max(0, Math.min(getArg0(1) - 1, mRows - 2));
        mBottomMargin = Math.max(mTopMargin + 2, Math.min(getArg1(mRows), mRows));
        setCursorPosition(0, 0);
      }
      break;
      case 's':
        if (isDecsetInternalBitSet(DECSET_BIT_LEFTRIGHT_MARGIN_MODE)) {
          mLeftMargin = Math.min(getArg0(1) - 1, mColumns - 2);
          mRightMargin = Math.max(mLeftMargin + 1, Math.min(getArg1(mColumns), mColumns));
          setCursorPosition(0, 0);
        } else {
          saveCursor();
        }
        break;
      case 't':
        switch (getArg0(0)) {
          case 11:
            mSession.write("\033[1t");
            break;
          case 13:
            mSession.write("\033[3;0;0t");
            break;
          case 14:
            mSession.write(String.format(Locale.US, "\033[4;%d;%dt", mRows * 12, mColumns * 12));
            break;
          case 18:
            mSession.write(String.format(Locale.US, "\033[8;%d;%dt", mRows, mColumns));
            break;
          case 19:
            mSession.write(String.format(Locale.US, "\033[9;%d;%dt", mRows, mColumns));
            break;
          case 20:
            mSession.write("\033]LIconLabel\033\\");
            break;
          case 21:
            mSession.write("\033]l\033\\");
            break;
          case 22:
            mTitleStack.push(mTitle);
            if (mTitleStack.size() > 20) {
              mTitleStack.remove(0);
            }
            break;
          case 23:
            if (!mTitleStack.isEmpty()) setTitle(mTitleStack.pop());
            break;
          default:
            break;
        }
        break;
      case 'u':
        restoreCursor();
        break;
      case ' ':
        continueSequence(ESC_CSI_ARGS_SPACE);
        break;
      default:
        parseArg(b);
        break;
    }
  }

  private void selectGraphicRendition() {
    if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
    for (int i = 0; i <= mArgIndex; i++) {
      int code = mArgs[i];
      if (code < 0) {
        if (mArgIndex > 0) {
          continue;
        } else {
          code = 0;
        }
      }
      if (code == 0) {
        mForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
        mBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
        mEffect = 0;
      } else if (code == 1) {
        mEffect |= TextStyle.CHARACTER_ATTRIBUTE_BOLD;
      } else if (code == 2) {
        mEffect |= TextStyle.CHARACTER_ATTRIBUTE_DIM;
      } else if (code == 3) {
        mEffect |= TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
      } else if (code == 4) {
        mEffect |= TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
      } else if (code == 5) {
        mEffect |= TextStyle.CHARACTER_ATTRIBUTE_BLINK;
      } else if (code == 7) {
        mEffect |= TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
      } else if (code == 8) {
        mEffect |= TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
      } else if (code == 9) {
        mEffect |= TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
      } else if (code == 10) {
      } else if (code == 11) {
      } else if (code == 22) {
        mEffect &= ~(TextStyle.CHARACTER_ATTRIBUTE_BOLD | TextStyle.CHARACTER_ATTRIBUTE_DIM);
      } else if (code == 23) {
        mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_ITALIC;
      } else if (code == 24) {
        mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE;
      } else if (code == 25) {
        mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_BLINK;
      } else if (code == 27) {
        mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_INVERSE;
      } else if (code == 28) {
        mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE;
      } else if (code == 29) {
        mEffect &= ~TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH;
      } else if (code >= 30 && code <= 37) {
        mForeColor = code - 30;
      } else if (code == 38 || code == 48) {
        if (i + 2 > mArgIndex) continue;
        int firstArg = mArgs[i + 1];
        if (firstArg == 2) {
          if (i + 4 > mArgIndex) {
            Log.w(EmulatorDebug.LOG_TAG, "Too few CSI" + code + ";2 RGB arguments");
          } else {
            int red = mArgs[i + 2], green = mArgs[i + 3], blue = mArgs[i + 4];
            if (red < 0 || green < 0 || blue < 0 || red > 255 || green > 255 || blue > 255) {
              finishSequenceAndLogError("Invalid RGB: " + red + "," + green + "," + blue);
            } else {
              int argbColor = 0xff000000 | (red << 16) | (green << 8) | blue;
              if (code == 38) {
                mForeColor = argbColor;
              } else {
                mBackColor = argbColor;
              }
            }
            i += 4;
          }
        } else if (firstArg == 5) {
          int color = mArgs[i + 2];
          i += 2;
          if (color >= 0 && color < TextStyle.NUM_INDEXED_COLORS) {
            if (code == 38) {
              mForeColor = color;
            } else {
              mBackColor = color;
            }
          } else {
            if (LOG_ESCAPE_SEQUENCES)
              Log.w(EmulatorDebug.LOG_TAG, "Invalid color index: " + color);
          }
        } else {
          finishSequenceAndLogError("Invalid ISO-8613-3 SGR first argument: " + firstArg);
        }
      } else if (code == 39) {
        mForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
      } else if (code >= 40 && code <= 47) {
        mBackColor = code - 40;
      } else if (code == 49) {
        mBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
      } else if (code >= 90 && code <= 97) {
        mForeColor = code - 90 + 8;
      } else if (code >= 100 && code <= 107) {
        mBackColor = code - 100 + 8;
      } else {
        if (LOG_ESCAPE_SEQUENCES)
          Log.w(EmulatorDebug.LOG_TAG, String.format("SGR unknown code %d", code));
      }
    }
  }

  private void doOsc(int b) {
    switch (b) {
      case 7:
        doOscSetTextParameters("\007");
        break;
      case 27:
        continueSequence(ESC_OSC_ESC);
        break;
      default:
        collectOSCArgs(b);
        break;
    }
  }

  private void doOscEsc(int b) {
    switch (b) {
      case '\\':
        doOscSetTextParameters("\033\\");
        break;
      default:
        collectOSCArgs(27);
        collectOSCArgs(b);
        continueSequence(ESC_OSC);
        break;
    }
  }

  private void doOscSetTextParameters(String bellOrStringTerminator) {
    int value = -1;
    String textParameter = "";
    for (int mOSCArgTokenizerIndex = 0; mOSCArgTokenizerIndex < mOSCOrDeviceControlArgs.length(); mOSCArgTokenizerIndex++) {
      char b = mOSCOrDeviceControlArgs.charAt(mOSCArgTokenizerIndex);
      if (b == ';') {
        textParameter = mOSCOrDeviceControlArgs.substring(mOSCArgTokenizerIndex + 1);
        break;
      } else if (b >= '0' && b <= '9') {
        value = ((value < 0) ? 0 : value * 10) + (b - '0');
      } else {
        unknownSequence(b);
        return;
      }
    }

    switch (value) {
      case 0:
      case 1:
      case 2:
        setTitle(textParameter);
        break;
      case 4:
        int colorIndex = -1;
        int parsingPairStart = -1;
        for (int i = 0; ; i++) {
          boolean endOfInput = i == textParameter.length();
          char b = endOfInput ? ';' : textParameter.charAt(i);
          if (b == ';') {
            if (parsingPairStart < 0) {
              parsingPairStart = i + 1;
            } else {
              if (colorIndex < 0 || colorIndex > 255) {
                unknownSequence(b);
                return;
              } else {
                mColors.tryParseColor(colorIndex, textParameter.substring(parsingPairStart, i));
                mSession.onColorsChanged();
                colorIndex = -1;
                parsingPairStart = -1;
              }
            }
          } else if (parsingPairStart >= 0) {
          } else if (parsingPairStart < 0 && (b >= '0' && b <= '9')) {
            colorIndex = ((colorIndex < 0) ? 0 : colorIndex * 10) + (b - '0');
          } else {
            unknownSequence(b);
            return;
          }
          if (endOfInput) break;
        }
        break;
      case 10:
      case 11:
      case 12:
        int specialIndex = TextStyle.COLOR_INDEX_FOREGROUND + (value - 10);
        int lastSemiIndex = 0;
        for (int charIndex = 0; ; charIndex++) {
          boolean endOfInput = charIndex == textParameter.length();
          if (endOfInput || textParameter.charAt(charIndex) == ';') {
            try {
              String colorSpec = textParameter.substring(lastSemiIndex, charIndex);
              if ("?".equals(colorSpec)) {
                int rgb = mColors.mCurrentColors[specialIndex];
                int r = (65535 * ((rgb & 0x00FF0000) >> 16)) / 255;
                int g = (65535 * ((rgb & 0x0000FF00) >> 8)) / 255;
                int b = (65535 * ((rgb & 0x000000FF))) / 255;
                mSession.write("\033]" + value + ";rgb:" + String.format(Locale.US, "%04x", r) + "/" + String.format(Locale.US, "%04x", g) + "/"
                  + String.format(Locale.US, "%04x", b) + bellOrStringTerminator);
              } else {
                mColors.tryParseColor(specialIndex, colorSpec);
                mSession.onColorsChanged();
              }
              specialIndex++;
              if (endOfInput || (specialIndex > TextStyle.COLOR_INDEX_CURSOR) || ++charIndex >= textParameter.length())
                break;
              lastSemiIndex = charIndex;
            } catch (NumberFormatException e) {
            }
          }
        }
        break;
      case 52:
        int startIndex = textParameter.indexOf(";") + 1;
        try {
          String clipboardText = new String(Base64.decode(textParameter.substring(startIndex), 0), StandardCharsets.UTF_8);
          mSession.clipboardText(clipboardText);
        } catch (Exception e) {
          Log.e(EmulatorDebug.LOG_TAG, "OSC Manipulate selection, invalid string '" + textParameter + "");
        }
        break;
      case 104:
        if (textParameter.isEmpty()) {
          mColors.reset();
          mSession.onColorsChanged();
        } else {
          int lastIndex = 0;
          for (int charIndex = 0; ; charIndex++) {
            boolean endOfInput = charIndex == textParameter.length();
            if (endOfInput || textParameter.charAt(charIndex) == ';') {
              try {
                int colorToReset = Integer.parseInt(textParameter.substring(lastIndex, charIndex));
                mColors.reset(colorToReset);
                mSession.onColorsChanged();
                if (endOfInput) break;
                charIndex++;
                lastIndex = charIndex;
              } catch (NumberFormatException e) {
              }
            }
          }
        }
        break;
      case 110:
      case 111:
      case 112:
        mColors.reset(TextStyle.COLOR_INDEX_FOREGROUND + (value - 110));
        mSession.onColorsChanged();
        break;
      case 119:
        break;
      default:
        unknownParameter(value);
        break;
    }
    finishSequence();
  }

  private void blockClear(int sx, int sy, int w) {
    blockClear(sx, sy, w, 1);
  }

  private void blockClear(int sx, int sy, int w, int h) {
    mScreen.blockSet(sx, sy, w, h, ' ', getStyle());
  }

  private long getStyle() {
    return TextStyle.encode(mForeColor, mBackColor, mEffect);
  }

  private void doSetMode(boolean newValue) {
    int modeBit = getArg0(0);
    switch (modeBit) {
      case 4:
        mInsertMode = newValue;
        break;
      case 20:
        unknownParameter(modeBit);
        break;
      case 34:
        break;
      default:
        unknownParameter(modeBit);
        break;
    }
  }

  private void setCursorPosition(int x, int y) {
    boolean originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE);
    int effectiveTopMargin = originMode ? mTopMargin : 0;
    int effectiveBottomMargin = originMode ? mBottomMargin : mRows;
    int effectiveLeftMargin = originMode ? mLeftMargin : 0;
    int effectiveRightMargin = originMode ? mRightMargin : mColumns;
    int newRow = Math.max(effectiveTopMargin, Math.min(effectiveTopMargin + y, effectiveBottomMargin - 1));
    int newCol = Math.max(effectiveLeftMargin, Math.min(effectiveLeftMargin + x, effectiveRightMargin - 1));
    setCursorRowCol(newRow, newCol);
  }

  private void scrollDownOneLine() {
    mScrollCounter++;
    if (mLeftMargin != 0 || mRightMargin != mColumns) {
      mScreen.blockCopy(mLeftMargin, mTopMargin + 1, mRightMargin - mLeftMargin, mBottomMargin - mTopMargin - 1, mLeftMargin, mTopMargin);
      mScreen.blockSet(mLeftMargin, mBottomMargin - 1, mRightMargin - mLeftMargin, 1, ' ', mEffect);
    } else {
      mScreen.scrollDownOneLine(mTopMargin, mBottomMargin, getStyle());
    }
  }

  private void parseArg(int inputByte) {
    int[] bytes = new int[]{inputByte};
    if (mEscapeState == ESC_CSI) {
      if ((mIsCSIStart && inputByte == ';') ||
        (!mIsCSIStart && mLastCSIArg != null && mLastCSIArg == ';'  && inputByte == ';')) {
        bytes = new int[]{'0', ';'};
      }
    }

    mIsCSIStart = false;

    for (int b : bytes) {
      if (b >= '0' && b <= '9') {
        if (mArgIndex < mArgs.length) {
          int oldValue = mArgs[mArgIndex];
          int thisDigit = b - '0';
          int value;
          if (oldValue >= 0) {
            value = oldValue * 10 + thisDigit;
          } else {
            value = thisDigit;
          }
          mArgs[mArgIndex] = value;
        }
        continueSequence(mEscapeState);
      } else if (b == ';') {
        if (mArgIndex < mArgs.length) {
          mArgIndex++;
        }
        continueSequence(mEscapeState);
      } else {
        unknownSequence(b);
      }
      mLastCSIArg = b;
    }
  }

  private int getArg0(int defaultValue) {
    return getArg(0, defaultValue, true);
  }

  private int getArg1(int defaultValue) {
    return getArg(1, defaultValue, true);
  }

  private int getArg(int index, int defaultValue, boolean treatZeroAsDefault) {
    int result = mArgs[index];
    if (result < 0 || (result == 0 && treatZeroAsDefault)) {
      result = defaultValue;
    }
    return result;
  }

  private void collectOSCArgs(int b) {
    if (mOSCOrDeviceControlArgs.length() < MAX_OSC_STRING_LENGTH) {
      mOSCOrDeviceControlArgs.appendCodePoint(b);
      continueSequence(mEscapeState);
    } else {
      unknownSequence(b);
    }
  }

  private void unimplementedSequence(int b) {
    logError("Unimplemented sequence char '" + (char) b + "' (U+" + String.format("%04x", b) + ")");
    finishSequence();
  }

  private void unknownSequence(int b) {
    logError("Unknown sequence char '" + (char) b + "' (numeric value=" + b + ")");
    finishSequence();
  }

  private void unknownParameter(int parameter) {
    logError("Unknown parameter: " + parameter);
    finishSequence();
  }

  private void logError(String errorType) {
    if (LOG_ESCAPE_SEQUENCES) {
      StringBuilder buf = new StringBuilder();
      buf.append(errorType);
      buf.append(", escapeState=");
      buf.append(mEscapeState);
      boolean firstArg = true;
      if (mArgIndex >= mArgs.length) mArgIndex = mArgs.length - 1;
      for (int i = 0; i <= mArgIndex; i++) {
        int value = mArgs[i];
        if (value >= 0) {
          if (firstArg) {
            firstArg = false;
            buf.append(", args={");
          } else {
            buf.append(',');
          }
          buf.append(value);
        }
      }
      if (!firstArg) buf.append('}');
      finishSequenceAndLogError(buf.toString());
    }
  }

  private void finishSequenceAndLogError(String error) {
    if (LOG_ESCAPE_SEQUENCES) Log.w(EmulatorDebug.LOG_TAG, error);
    finishSequence();
  }

  private void finishSequence() {
    mEscapeState = ESC_NONE;
  }

  private void emitCodePoint(int codePoint) {
    mLastEmittedCodePoint = codePoint;
    if (mUseLineDrawingUsesG0 ? mUseLineDrawingG0 : mUseLineDrawingG1) {
      switch (codePoint) {
        case '_':
          codePoint = ' ';
          break;
        case '`':
          codePoint = '◆';
          break;
        case '0':
          codePoint = '█';
          break;
        case 'a':
          codePoint = '▒';
          break;
        case 'b':
          codePoint = '␉';
          break;
        case 'c':
          codePoint = '␌';
          break;
        case 'd':
          codePoint = '\r';
          break;
        case 'e':
          codePoint = '␊';
          break;
        case 'f':
          codePoint = '°';
          break;
        case 'g':
          codePoint = '±';
          break;
        case 'h':
          codePoint = '\n';
          break;
        case 'i':
          codePoint = '␋';
          break;
        case 'j':
          codePoint = '┘';
          break;
        case 'k':
          codePoint = '┐';
          break;
        case 'l':
          codePoint = '┌';
          break;
        case 'm':
          codePoint = '└';
          break;
        case 'n':
          codePoint = '┼';
          break;
        case 'o':
          codePoint = '⎺';
          break;
        case 'p':
          codePoint = '⎻';
          break;
        case 'q':
          codePoint = '─';
          break;
        case 'r':
          codePoint = '⎼';
          break;
        case 's':
          codePoint = '⎽';
          break;
        case 't':
          codePoint = '├';
          break;
        case 'u':
          codePoint = '┤';
          break;
        case 'v':
          codePoint = '┴';
          break;
        case 'w':
          codePoint = '┬';
          break;
        case 'x':
          codePoint = '│';
          break;
        case 'y':
          codePoint = '≤';
          break;
        case 'z':
          codePoint = '≥';
          break;
        case '{':
          codePoint = 'π';
          break;
        case '|':
          codePoint = '≠';
          break;
        case '}':
          codePoint = '£';
          break;
        case '~':
          codePoint = '·';
          break;
      }
    }

    final boolean autoWrap = isDecsetInternalBitSet(DECSET_BIT_AUTOWRAP);
    final int displayWidth = WcWidth.width(codePoint);
    final boolean cursorInLastColumn = mCursorCol == mRightMargin - 1;

    if (autoWrap) {
      if (cursorInLastColumn && ((mAboutToAutoWrap && displayWidth == 1) || displayWidth == 2)) {
        mScreen.setLineWrap(mCursorRow);
        mCursorCol = mLeftMargin;
        if (mCursorRow + 1 < mBottomMargin) {
          mCursorRow++;
        } else {
          scrollDownOneLine();
        }
      }
    } else if (cursorInLastColumn && displayWidth == 2) {
      return;
    }

    if (mInsertMode && displayWidth > 0) {
      int destCol = mCursorCol + displayWidth;
      if (destCol < mRightMargin)
        mScreen.blockCopy(mCursorCol, mCursorRow, mRightMargin - destCol, 1, destCol, mCursorRow);
    }

    int offsetDueToCombiningChar = ((displayWidth <= 0 && mCursorCol > 0 && !mAboutToAutoWrap) ? 1 : 0);
    mScreen.setChar(mCursorCol - offsetDueToCombiningChar, mCursorRow, codePoint, getStyle());

    if (autoWrap && displayWidth > 0)
      mAboutToAutoWrap = (mCursorCol == mRightMargin - displayWidth);

    mCursorCol = Math.min(mCursorCol + displayWidth, mRightMargin - 1);
  }

  private void setCursorRow(int row) {
    mCursorRow = row;
    mAboutToAutoWrap = false;
  }

  private void setCursorCol(int col) {
    mCursorCol = col;
    mAboutToAutoWrap = false;
  }

  private void setCursorColRespectingOriginMode(int col) {
    setCursorPosition(col, mCursorRow);
  }

  private void setCursorRowCol(int row, int col) {
    mCursorRow = Math.max(0, Math.min(row, mRows - 1));
    mCursorCol = Math.max(0, Math.min(col, mColumns - 1));
    mAboutToAutoWrap = false;
  }

  public int getScrollCounter() {
    return mScrollCounter;
  }

  public void clearScrollCounter() {
    mScrollCounter = 0;
  }

  public void reset() {
    mCursorStyle = CURSOR_STYLE_BLOCK;
    mArgIndex = 0;
    mContinueSequence = false;
    mEscapeState = ESC_NONE;
    mInsertMode = false;
    mTopMargin = mLeftMargin = 0;
    mBottomMargin = mRows;
    mRightMargin = mColumns;
    mAboutToAutoWrap = false;
    mForeColor = mSavedStateMain.mSavedForeColor = mSavedStateAlt.mSavedForeColor = TextStyle.COLOR_INDEX_FOREGROUND;
    mBackColor = mSavedStateMain.mSavedBackColor = mSavedStateAlt.mSavedBackColor = TextStyle.COLOR_INDEX_BACKGROUND;
    setDefaultTabStops();

    mUseLineDrawingG0 = mUseLineDrawingG1 = false;
    mUseLineDrawingUsesG0 = true;

    mSavedStateMain.mSavedCursorRow = mSavedStateMain.mSavedCursorCol = mSavedStateMain.mSavedEffect = mSavedStateMain.mSavedDecFlags = 0;
    mSavedStateAlt.mSavedCursorRow = mSavedStateAlt.mSavedCursorCol = mSavedStateAlt.mSavedEffect = mSavedStateAlt.mSavedDecFlags = 0;
    mCurrentDecSetFlags = 0;
    setDecsetinternalBit(DECSET_BIT_AUTOWRAP, true);
    setDecsetinternalBit(DECSET_BIT_SHOWING_CURSOR, true);
    mSavedDecSetFlags = mSavedStateMain.mSavedDecFlags = mSavedStateAlt.mSavedDecFlags = mCurrentDecSetFlags;

    mUtf8Index = mUtf8ToFollow = 0;

    mColors.reset();
    mSession.onColorsChanged();
  }

  public void setColorScheme(TerminalColorScheme colorScheme) {
    mColors.reset(colorScheme);
    mSession.onColorsChanged();
  }

  public String getSelectedText(int x1, int y1, int x2, int y2) {
    return mScreen.getSelectedText(x1, y1, x2, y2);
  }

  public String getTitle() {
    return mTitle;
  }

  private void setTitle(String newTitle) {
    String oldTitle = mTitle;
    mTitle = newTitle;
    if (!Objects.equals(oldTitle, newTitle)) {
      mSession.titleChanged(oldTitle, newTitle);
    }
  }

  public void paste(String text) {
    text = text.replaceAll("(\u001B|[\u0080-\u009F])", "");
    text = text.replaceAll("\r?\n", "\r");

    boolean bracketed = isDecsetInternalBitSet(DECSET_BIT_BRACKETED_PASTE_MODE);
    if (bracketed) mSession.write("\033[200~");
    mSession.write(text);
    if (bracketed) mSession.write("\033[201~");
  }

  static final class SavedScreenState {
    int mSavedCursorRow, mSavedCursorCol;
    int mSavedEffect, mSavedForeColor, mSavedBackColor;
    int mSavedDecFlags;
    boolean mUseLineDrawingG0, mUseLineDrawingG1, mUseLineDrawingUsesG0 = true;
  }

  @Override
  public String toString() {
    return "TerminalEmulator[size=" + mScreen.mColumns + "x" + mScreen.mScreenRows + ", margins={" + mTopMargin + "," + mRightMargin + "," + mBottomMargin
      + "," + mLeftMargin + "}]";
  }

}
