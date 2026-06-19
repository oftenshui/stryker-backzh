package com.stryker.terminal.backend;

public final class TextStyle {

  public final static int CHARACTER_ATTRIBUTE_BOLD = 1;
  public final static int CHARACTER_ATTRIBUTE_ITALIC = 1 << 1;
  public final static int CHARACTER_ATTRIBUTE_UNDERLINE = 1 << 2;
  public final static int CHARACTER_ATTRIBUTE_BLINK = 1 << 3;
  public final static int CHARACTER_ATTRIBUTE_INVERSE = 1 << 4;
  public final static int CHARACTER_ATTRIBUTE_INVISIBLE = 1 << 5;
  public final static int CHARACTER_ATTRIBUTE_STRIKETHROUGH = 1 << 6;
  public final static int CHARACTER_ATTRIBUTE_PROTECTED = 1 << 7;
  public final static int CHARACTER_ATTRIBUTE_DIM = 1 << 8;
  private final static int CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND = 1 << 9;
  private final static int CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND = 1 << 10;

  public final static int COLOR_INDEX_FOREGROUND = 256;
  public final static int COLOR_INDEX_BACKGROUND = 257;
  public final static int COLOR_INDEX_CURSOR = 258;

  public final static int NUM_INDEXED_COLORS = 259;

  final static long NORMAL = encode(COLOR_INDEX_FOREGROUND, COLOR_INDEX_BACKGROUND, 0);

  static long encode(int foreColor, int backColor, int effect) {
    long result = effect & 0b111111111;
    if ((0xff000000 & foreColor) == 0xff000000) {
      result |= CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND | ((foreColor & 0x00ffffffL) << 40L);
    } else {
      result |= (foreColor & 0b111111111L) << 40;
    }
    if ((0xff000000 & backColor) == 0xff000000) {
      result |= CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND | ((backColor & 0x00ffffffL) << 16L);
    } else {
      result |= (backColor & 0b111111111L) << 16L;
    }

    return result;
  }

  public static int decodeForeColor(long style) {
    if ((style & CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND) == 0) {
      return (int) ((style >>> 40) & 0b111111111L);
    } else {
      return 0xff000000 | (int) ((style >>> 40) & 0x00ffffffL);
    }

  }

  public static int decodeBackColor(long style) {
    if ((style & CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND) == 0) {
      return (int) ((style >>> 16) & 0b111111111L);
    } else {
      return 0xff000000 | (int) ((style >>> 16) & 0x00ffffffL);
    }
  }

  public static int decodeEffect(long style) {
    return (int) (style & 0b11111111111);
  }

}
