package com.stryker.terminal.frontend.session.view;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.stryker.terminal.backend.TerminalSession;
import com.stryker.terminal.backend.TerminalSession;

public interface TerminalViewClient {
  float onScale(float scale);

  void onSingleTapUp(MotionEvent e);

  boolean shouldBackButtonBeMappedToEscape();

  void copyModeChanged(boolean copyMode);

  boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session);

  boolean onKeyUp(int keyCode, KeyEvent e);

  boolean readControlKey();

  boolean readAltKey();

  boolean readShiftKey();

  boolean readFnKey();

  boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session);

  boolean onLongPress(MotionEvent event);

}
