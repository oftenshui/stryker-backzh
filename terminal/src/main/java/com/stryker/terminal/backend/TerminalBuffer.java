package com.stryker.terminal.backend;

import java.util.Arrays;

public final class TerminalBuffer {

  TerminalRow[] mLines;
  int mTotalRows;
  int mScreenRows, mColumns;
  private int mActiveTranscriptRows = 0;
  private int mScreenFirstRow = 0;

  public TerminalBuffer(int columns, int totalRows, int screenRows) {
    mColumns = columns;
    mTotalRows = totalRows;
    mScreenRows = screenRows;
    mLines = new TerminalRow[totalRows];

    blockSet(0, 0, columns, screenRows, ' ', TextStyle.NORMAL);
  }

  public String getTranscriptText() {
    return getSelectedText(0, -getActiveTranscriptRows(), mColumns, mScreenRows).trim();
  }

  public String getTranscriptTextWithoutJoinedLines() {
    return getSelectedText(0, -getActiveTranscriptRows(), mColumns, mScreenRows, false).trim();
  }

  public String getSelectedText(int selX1, int selY1, int selX2, int selY2) {
    return getSelectedText(selX1, selY1, selX2, selY2, true);
  }

  public String getSelectedText(int selX1, int selY1, int selX2, int selY2, boolean joinBackLines) {
    final StringBuilder builder = new StringBuilder();
    final int columns = mColumns;

    if (selY1 < -getActiveTranscriptRows()) selY1 = -getActiveTranscriptRows();
    if (selY2 >= mScreenRows) selY2 = mScreenRows - 1;

    for (int row = selY1; row <= selY2; row++) {
      int x1 = (row == selY1) ? selX1 : 0;
      int x2;
      if (row == selY2) {
        x2 = selX2 + 1;
        if (x2 > columns) x2 = columns;
      } else {
        x2 = columns;
      }
      TerminalRow lineObject = mLines[externalToInternalRow(row)];
      int x1Index = lineObject.findStartOfColumn(x1);
      int x2Index = (x2 < mColumns) ? lineObject.findStartOfColumn(x2) : lineObject.getSpaceUsed();
      if (x2Index == x1Index) {
        x2Index = lineObject.findStartOfColumn(x2 + 1);
      }
      char[] line = lineObject.mText;
      int lastPrintingCharIndex = -1;
      int i;
      boolean rowLineWrap = getLineWrap(row);
      if (rowLineWrap && x2 == columns) {
        lastPrintingCharIndex = x2Index - 1;
      } else {
        for (i = x1Index; i < x2Index; ++i) {
          char c = line[i];
          if (c != ' ') lastPrintingCharIndex = i;
        }
      }

      int len = lastPrintingCharIndex - x1Index + 1;
      if (lastPrintingCharIndex != -1 && len > 0)
        builder.append(line, x1Index, len);

      if ((!joinBackLines || !rowLineWrap)
        && row < selY2 && row < mScreenRows - 1) builder.append('\n');
    }
    return builder.toString();
  }

  public int getActiveTranscriptRows() {
    return mActiveTranscriptRows;
  }

  public int getActiveRows() {
    return mActiveTranscriptRows + mScreenRows;
  }

  public int externalToInternalRow(int externalRow) {
    if (externalRow < -mActiveTranscriptRows || externalRow > mScreenRows)
      throw new IllegalArgumentException("extRow=" + externalRow + ", mScreenRows=" + mScreenRows + ", mActiveTranscriptRows=" + mActiveTranscriptRows);
    final int internalRow = mScreenFirstRow + externalRow;
    return (internalRow < 0) ? (mTotalRows + internalRow) : (internalRow % mTotalRows);
  }

  public void setLineWrap(int row) {
    mLines[externalToInternalRow(row)].mLineWrap = true;
  }

  public boolean getLineWrap(int row) {
    return mLines[externalToInternalRow(row)].mLineWrap;
  }

  public void clearLineWrap(int row) {
    mLines[externalToInternalRow(row)].mLineWrap = false;
  }

  public void resize(int newColumns, int newRows, int newTotalRows, int[] cursor, long currentStyle, boolean altScreen) {
    if (newColumns == mColumns && newRows <= mTotalRows) {
      int shiftDownOfTopRow = mScreenRows - newRows;
      if (shiftDownOfTopRow > 0 && shiftDownOfTopRow < mScreenRows) {
        for (int i = mScreenRows - 1; i > 0; i--) {
          if (cursor[1] >= i) break;
          int r = externalToInternalRow(i);
          if (mLines[r] == null || mLines[r].isBlank()) {
            if (--shiftDownOfTopRow == 0) break;
          }
        }
      } else if (shiftDownOfTopRow < 0) {
        int actualShift = Math.max(shiftDownOfTopRow, -mActiveTranscriptRows);
        if (shiftDownOfTopRow != actualShift) {
          for (int i = 0; i < actualShift - shiftDownOfTopRow; i++)
            allocateFullLineIfNecessary((mScreenFirstRow + mScreenRows + i) % mTotalRows).clear(currentStyle);
          shiftDownOfTopRow = actualShift;
        }
      }
      mScreenFirstRow += shiftDownOfTopRow;
      mScreenFirstRow = (mScreenFirstRow < 0) ? (mScreenFirstRow + mTotalRows) : (mScreenFirstRow % mTotalRows);
      mTotalRows = newTotalRows;
      mActiveTranscriptRows = altScreen ? 0 : Math.max(0, mActiveTranscriptRows + shiftDownOfTopRow);
      cursor[1] -= shiftDownOfTopRow;
      mScreenRows = newRows;
    } else {
      TerminalRow[] oldLines = mLines;
      mLines = new TerminalRow[newTotalRows];
      for (int i = 0; i < newTotalRows; i++)
        mLines[i] = new TerminalRow(newColumns, currentStyle);

      final int oldActiveTranscriptRows = mActiveTranscriptRows;
      final int oldScreenFirstRow = mScreenFirstRow;
      final int oldScreenRows = mScreenRows;
      final int oldTotalRows = mTotalRows;
      mTotalRows = newTotalRows;
      mScreenRows = newRows;
      mActiveTranscriptRows = mScreenFirstRow = 0;
      mColumns = newColumns;

      int newCursorRow = -1;
      int newCursorColumn = -1;
      int oldCursorRow = cursor[1];
      int oldCursorColumn = cursor[0];
      boolean newCursorPlaced = false;

      int currentOutputExternalRow = 0;
      int currentOutputExternalColumn = 0;

      int skippedBlankLines = 0;
      for (int externalOldRow = -oldActiveTranscriptRows; externalOldRow < oldScreenRows; externalOldRow++) {
        int internalOldRow = oldScreenFirstRow + externalOldRow;
        internalOldRow = (internalOldRow < 0) ? (oldTotalRows + internalOldRow) : (internalOldRow % oldTotalRows);

        TerminalRow oldLine = oldLines[internalOldRow];
        boolean cursorAtThisRow = externalOldRow == oldCursorRow;
        if (oldLine == null || (!(!newCursorPlaced && cursorAtThisRow)) && oldLine.isBlank()) {
          skippedBlankLines++;
          continue;
        } else if (skippedBlankLines > 0) {
          for (int i = 0; i < skippedBlankLines; i++) {
            if (currentOutputExternalRow == mScreenRows - 1) {
              scrollDownOneLine(0, mScreenRows, currentStyle);
            } else {
              currentOutputExternalRow++;
            }
            currentOutputExternalColumn = 0;
          }
          skippedBlankLines = 0;
        }

        int lastNonSpaceIndex = 0;
        boolean justToCursor = false;
        if (cursorAtThisRow || oldLine.mLineWrap) {
          lastNonSpaceIndex = oldLine.getSpaceUsed();
          if (cursorAtThisRow) justToCursor = true;
        } else {
          for (int i = 0; i < oldLine.getSpaceUsed(); i++)
            if (oldLine.mText[i] != ' ')
              lastNonSpaceIndex = i + 1;
        }

        int currentOldCol = 0;
        long styleAtCol = 0;
        for (int i = 0; i < lastNonSpaceIndex; i++) {
          char c = oldLine.mText[i];
          int codePoint = (Character.isHighSurrogate(c)) ? Character.toCodePoint(c, oldLine.mText[++i]) : c;
          int displayWidth = WcWidth.width(codePoint);
          if (displayWidth > 0) styleAtCol = oldLine.getStyle(currentOldCol);

          if (currentOutputExternalColumn + displayWidth > mColumns) {
            setLineWrap(currentOutputExternalRow);
            if (currentOutputExternalRow == mScreenRows - 1) {
              if (newCursorPlaced) newCursorRow--;
              scrollDownOneLine(0, mScreenRows, currentStyle);
            } else {
              currentOutputExternalRow++;
            }
            currentOutputExternalColumn = 0;
          }

          int offsetDueToCombiningChar = ((displayWidth <= 0 && currentOutputExternalColumn > 0) ? 1 : 0);
          int outputColumn = currentOutputExternalColumn - offsetDueToCombiningChar;
          setChar(outputColumn, currentOutputExternalRow, codePoint, styleAtCol);

          if (displayWidth > 0) {
            if (oldCursorRow == externalOldRow && oldCursorColumn == currentOldCol) {
              newCursorColumn = currentOutputExternalColumn;
              newCursorRow = currentOutputExternalRow;
              newCursorPlaced = true;
            }
            currentOldCol += displayWidth;
            currentOutputExternalColumn += displayWidth;
            if (justToCursor && newCursorPlaced) break;
          }
        }
        if (externalOldRow != (oldScreenRows - 1) && !oldLine.mLineWrap) {
          if (currentOutputExternalRow == mScreenRows - 1) {
            if (newCursorPlaced) newCursorRow--;
            scrollDownOneLine(0, mScreenRows, currentStyle);
          } else {
            currentOutputExternalRow++;
          }
          currentOutputExternalColumn = 0;
        }
      }

      cursor[0] = newCursorColumn;
      cursor[1] = newCursorRow;
    }

    if (cursor[0] < 0 || cursor[1] < 0) cursor[0] = cursor[1] = 0;
  }

  private void blockCopyLinesDown(int srcInternal, int len) {
    if (len == 0) return;
    int totalRows = mTotalRows;

    int start = len - 1;
    TerminalRow lineToBeOverWritten = mLines[(srcInternal + start + 1) % totalRows];
    for (int i = start; i >= 0; --i)
      mLines[(srcInternal + i + 1) % totalRows] = mLines[(srcInternal + i) % totalRows];
    mLines[(srcInternal) % totalRows] = lineToBeOverWritten;
  }

  public void scrollDownOneLine(int topMargin, int bottomMargin, long style) {
    if (topMargin > bottomMargin - 1 || topMargin < 0 || bottomMargin > mScreenRows)
      throw new IllegalArgumentException("topMargin=" + topMargin + ", bottomMargin=" + bottomMargin + ", mScreenRows=" + mScreenRows);

    blockCopyLinesDown(mScreenFirstRow, topMargin);
    blockCopyLinesDown(externalToInternalRow(bottomMargin), mScreenRows - bottomMargin);

    mScreenFirstRow = (mScreenFirstRow + 1) % mTotalRows;
    if (mActiveTranscriptRows < mTotalRows - mScreenRows) mActiveTranscriptRows++;

    int blankRow = externalToInternalRow(bottomMargin - 1);
    if (mLines[blankRow] == null) {
      mLines[blankRow] = new TerminalRow(mColumns, style);
    } else {
      mLines[blankRow].clear(style);
    }
  }

  public void blockCopy(int sx, int sy, int w, int h, int dx, int dy) {
    if (w == 0) return;
    if (sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows || dx < 0 || dx + w > mColumns || dy < 0 || dy + h > mScreenRows)
      throw new IllegalArgumentException();
    boolean copyingUp = sy > dy;
    for (int y = 0; y < h; y++) {
      int y2 = copyingUp ? y : (h - (y + 1));
      TerminalRow sourceRow = allocateFullLineIfNecessary(externalToInternalRow(sy + y2));
      allocateFullLineIfNecessary(externalToInternalRow(dy + y2)).copyInterval(sourceRow, sx, sx + w, dx);
    }
  }

  public void blockSet(int sx, int sy, int w, int h, int val, long style) {
    if (sx < 0 || sx + w > mColumns || sy < 0 || sy + h > mScreenRows) {
      throw new IllegalArgumentException(
        "Illegal arguments! blockSet(" + sx + ", " + sy + ", " + w + ", " + h + ", " + val + ", " + mColumns + ", " + mScreenRows + ")");
    }
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++)
        setChar(sx + x, sy + y, val, style);
  }

  public TerminalRow allocateFullLineIfNecessary(int row) {
    return (mLines[row] == null) ? (mLines[row] = new TerminalRow(mColumns, 0)) : mLines[row];
  }

  public void setChar(int column, int row, int codePoint, long style) {
    if (row >= mScreenRows || column >= mColumns)
      throw new IllegalArgumentException("row=" + row + ", column=" + column + ", mScreenRows=" + mScreenRows + ", mColumns=" + mColumns);
    row = externalToInternalRow(row);
    allocateFullLineIfNecessary(row).setChar(column, codePoint, style);
  }

  public long getStyleAt(int externalRow, int column) {
    return allocateFullLineIfNecessary(externalToInternalRow(externalRow)).getStyle(column);
  }

  public void setOrClearEffect(int bits, boolean setOrClear, boolean reverse, boolean rectangular, int leftMargin, int rightMargin, int top, int left,
                               int bottom, int right) {
    for (int y = top; y < bottom; y++) {
      TerminalRow line = mLines[externalToInternalRow(y)];
      int startOfLine = (rectangular || y == top) ? left : leftMargin;
      int endOfLine = (rectangular || y + 1 == bottom) ? right : rightMargin;
      for (int x = startOfLine; x < endOfLine; x++) {
        long currentStyle = line.getStyle(x);
        int foreColor = TextStyle.decodeForeColor(currentStyle);
        int backColor = TextStyle.decodeBackColor(currentStyle);
        int effect = TextStyle.decodeEffect(currentStyle);
        if (reverse) {
          effect = (effect & ~bits) | (bits & ~effect);
        } else if (setOrClear) {
          effect |= bits;
        } else {
          effect &= ~bits;
        }
        line.mStyle[x] = TextStyle.encode(foreColor, backColor, effect);
      }
    }
  }

  public void clearTranscript() {
    if (mScreenFirstRow < mActiveTranscriptRows) {
      Arrays.fill(mLines, mTotalRows + mScreenFirstRow - mActiveTranscriptRows, mTotalRows, null);
      Arrays.fill(mLines, 0, mScreenFirstRow, null);
    } else {
      Arrays.fill(mLines, mScreenFirstRow - mActiveTranscriptRows, mScreenFirstRow, null);
    }
    mActiveTranscriptRows = 0;
  }
}
