package com.stryker.terminal.backend;

import java.nio.charset.StandardCharsets;

public abstract class TerminalOutput {

  public final void write(String data) {
    byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
    write(bytes, 0, bytes.length);
  }

  public abstract void write(byte[] data, int offset, int count);

  public abstract void titleChanged(String oldTitle, String newTitle);

  public abstract void clipboardText(String text);

  public abstract void onBell();

  public abstract void onColorsChanged();

}
