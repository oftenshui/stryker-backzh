package com.stryker.terminal.backend;

import java.util.Arrays;

public final class TerminalRow {

  private static final float SPARE_CAPACITY_FACTOR = 1.5f;

  private final int mColumns;
  public char[] mText;
  private short mSpaceUsed;
  boolean mLineWrap;
  final long[] mStyle;
  boolean mHasNonOneWidthOrSurrogateChars;

  public TerminalRow(int columns, long style) {
    mColumns = columns;
    mText = new char[(int) (SPARE_CAPACITY_FACTOR * columns)];
    mStyle = new long[columns];
    clear(style);
  }

  public void copyInterval(TerminalRow line, int sourceX1, int sourceX2, int destinationX) {
    mHasNonOneWidthOrSurrogateChars |= line.mHasNonOneWidthOrSurrogateChars;
    final int x1 = line.findStartOfColumn(sourceX1);
    final int x2 = line.findStartOfColumn(sourceX2);
    boolean startingFromSecondHalfOfWideChar = (sourceX1 > 0 && line.wideDisplayCharacterStartingAt(sourceX1 - 1));
    final char[] sourceChars = (this == line) ? Arrays.copyOf(line.mText, line.mText.length) : line.mText;
    int latestNonCombiningWidth = 0;
    for (int i = x1; i < x2; i++) {
      char sourceChar = sourceChars[i];
      int codePoint = Character.isHighSurrogate(sourceChar) ? Character.toCodePoint(sourceChar, sourceChars[++i]) : sourceChar;
      if (startingFromSecondHalfOfWideChar) {
        codePoint = ' ';
        startingFromSecondHalfOfWideChar = false;
      }
      int w = WcWidth.width(codePoint);
      if (w > 0) {
        destinationX += latestNonCombiningWidth;
        sourceX1 += latestNonCombiningWidth;
        latestNonCombiningWidth = w;
      }
      setChar(destinationX, codePoint, line.getStyle(sourceX1));
    }
  }

  public int getSpaceUsed() {
    return mSpaceUsed;
  }

  public int findStartOfColumn(int column) {
    if (column == mColumns) return getSpaceUsed();

    int currentColumn = 0;
    int currentCharIndex = 0;
    while (true) {
      int newCharIndex = currentCharIndex;
      char c = mText[newCharIndex++];
      boolean isHigh = Character.isHighSurrogate(c);
      int codePoint = isHigh ? Character.toCodePoint(c, mText[newCharIndex++]) : c;
      int wcwidth = WcWidth.width(codePoint);
      if (wcwidth > 0) {
        currentColumn += wcwidth;
        if (currentColumn == column) {
          while (newCharIndex < mSpaceUsed) {
            if (Character.isHighSurrogate(mText[newCharIndex])) {
              if (WcWidth.width(Character.toCodePoint(mText[newCharIndex], mText[newCharIndex + 1])) <= 0) {
                newCharIndex += 2;
              } else {
                break;
              }
            } else if (WcWidth.width(mText[newCharIndex]) <= 0) {
              newCharIndex++;
            } else {
              break;
            }
          }
          return newCharIndex;
        } else if (currentColumn > column) {
          return currentCharIndex;
        }
      }
      currentCharIndex = newCharIndex;
    }
  }

  private boolean wideDisplayCharacterStartingAt(int column) {
    for (int currentCharIndex = 0, currentColumn = 0; currentCharIndex < mSpaceUsed; ) {
      char c = mText[currentCharIndex++];
      int codePoint = Character.isHighSurrogate(c) ? Character.toCodePoint(c, mText[currentCharIndex++]) : c;
      int wcwidth = WcWidth.width(codePoint);
      if (wcwidth > 0) {
        if (currentColumn == column && wcwidth == 2) return true;
        currentColumn += wcwidth;
        if (currentColumn > column) return false;
      }
    }
    return false;
  }

  public void clear(long style) {
    Arrays.fill(mText, ' ');
    Arrays.fill(mStyle, style);
    mSpaceUsed = (short) mColumns;
    mHasNonOneWidthOrSurrogateChars = false;
  }

  public void setChar(int columnToSet, int codePoint, long style) {
    mStyle[columnToSet] = style;

    final int newCodePointDisplayWidth = WcWidth.width(codePoint);

    if (!mHasNonOneWidthOrSurrogateChars) {
      if (codePoint >= Character.MIN_SUPPLEMENTARY_CODE_POINT || newCodePointDisplayWidth != 1) {
        mHasNonOneWidthOrSurrogateChars = true;
      } else {
        mText[columnToSet] = (char) codePoint;
        return;
      }
    }

    final boolean newIsCombining = newCodePointDisplayWidth <= 0;

    boolean wasExtraColForWideChar = (columnToSet > 0) && wideDisplayCharacterStartingAt(columnToSet - 1);

    if (newIsCombining) {
      if (wasExtraColForWideChar) columnToSet--;
    } else {
      if (wasExtraColForWideChar) setChar(columnToSet - 1, ' ', style);
      boolean overwritingWideCharInNextColumn = newCodePointDisplayWidth == 2 && wideDisplayCharacterStartingAt(columnToSet + 1);
      if (overwritingWideCharInNextColumn) setChar(columnToSet + 1, ' ', style);
    }

    char[] text = mText;
    final int oldStartOfColumnIndex = findStartOfColumn(columnToSet);
    final int oldCodePointDisplayWidth = WcWidth.width(text, oldStartOfColumnIndex);

    int oldCharactersUsedForColumn;
    if (columnToSet + oldCodePointDisplayWidth < mColumns) {
      oldCharactersUsedForColumn = findStartOfColumn(columnToSet + oldCodePointDisplayWidth) - oldStartOfColumnIndex;
    } else {
      oldCharactersUsedForColumn = mSpaceUsed - oldStartOfColumnIndex;
    }

    int newCharactersUsedForColumn = Character.charCount(codePoint);
    if (newIsCombining) {
      newCharactersUsedForColumn += oldCharactersUsedForColumn;
    }

    int oldNextColumnIndex = oldStartOfColumnIndex + oldCharactersUsedForColumn;
    int newNextColumnIndex = oldStartOfColumnIndex + newCharactersUsedForColumn;

    final int javaCharDifference = newCharactersUsedForColumn - oldCharactersUsedForColumn;
    if (javaCharDifference > 0) {
      int oldCharactersAfterColumn = mSpaceUsed - oldNextColumnIndex;
      if (mSpaceUsed + javaCharDifference > text.length) {
        char[] newText = new char[text.length + mColumns];
        System.arraycopy(text, 0, newText, 0, oldStartOfColumnIndex + oldCharactersUsedForColumn);
        System.arraycopy(text, oldNextColumnIndex, newText, newNextColumnIndex, oldCharactersAfterColumn);
        mText = text = newText;
      } else {
        System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, oldCharactersAfterColumn);
      }
    } else if (javaCharDifference < 0) {
      System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, mSpaceUsed - oldNextColumnIndex);
    }
    mSpaceUsed += javaCharDifference;

    Character.toChars(codePoint, text, oldStartOfColumnIndex + (newIsCombining ? oldCharactersUsedForColumn : 0));

    if (oldCodePointDisplayWidth == 2 && newCodePointDisplayWidth == 1) {
      if (mSpaceUsed + 1 > text.length) {
        char[] newText = new char[text.length + mColumns];
        System.arraycopy(text, 0, newText, 0, newNextColumnIndex);
        System.arraycopy(text, newNextColumnIndex, newText, newNextColumnIndex + 1, mSpaceUsed - newNextColumnIndex);
        mText = text = newText;
      } else {
        System.arraycopy(text, newNextColumnIndex, text, newNextColumnIndex + 1, mSpaceUsed - newNextColumnIndex);
      }
      text[newNextColumnIndex] = ' ';

      ++mSpaceUsed;
    } else if (oldCodePointDisplayWidth == 1 && newCodePointDisplayWidth == 2) {
      if (columnToSet == mColumns - 1) {
        throw new IllegalArgumentException("Cannot put wide character in last column");
      } else if (columnToSet == mColumns - 2) {
        mSpaceUsed = (short) newNextColumnIndex;
      } else {
        int newNextNextColumnIndex = newNextColumnIndex + (Character.isHighSurrogate(mText[newNextColumnIndex]) ? 2 : 1);
        int nextLen = newNextNextColumnIndex - newNextColumnIndex;

        System.arraycopy(text, newNextNextColumnIndex, text, newNextColumnIndex, mSpaceUsed - newNextNextColumnIndex);
        mSpaceUsed -= nextLen;
      }
    }
  }

  boolean isBlank() {
    for (int charIndex = 0, charLen = getSpaceUsed(); charIndex < charLen; charIndex++)
      if (mText[charIndex] != ' ') return false;
    return true;
  }

  public final long getStyle(int column) {
    return mStyle[column];
  }

}
