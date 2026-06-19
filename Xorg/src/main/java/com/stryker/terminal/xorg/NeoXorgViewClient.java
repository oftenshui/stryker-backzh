package com.stryker.terminal.xorg;

import android.content.Context;
import android.view.Window;
import android.view.WindowManager;
import com.stryker.terminal.NeoGLView;

public interface NeoXorgViewClient {
  Context getContext();

  boolean isKeyboardWithoutTextInputShown();

  void showScreenKeyboardWithoutTextInputField(int flags);

  void setScreenKeyboardHintMessage(String hideMessage);

  boolean isScreenKeyboardShown();

  void showScreenKeyboard(String message);

  void hideScreenKeyboard();

  void runOnUiThread(Runnable runnable);

  void updateScreenOrientation();

  void initScreenOrientation();

  boolean isRunningOnOUYA();

  NeoGLView getGLView();

  Window getWindow();

  WindowManager getWindowManager();

  void setSystemMousePointerVisible(int visible);

  boolean isPaused();
}
