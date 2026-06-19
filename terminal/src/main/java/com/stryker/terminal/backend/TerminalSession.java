package com.stryker.terminal.backend;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class TerminalSession extends TerminalOutput {

  public interface SessionChangedCallback {
    void onTextChanged(TerminalSession changedSession);

    void onTitleChanged(TerminalSession changedSession);

    void onSessionFinished(TerminalSession finishedSession);

    void onClipboardText(TerminalSession session, String text);

    void onBell(TerminalSession session);

    void onColorsChanged(TerminalSession session);

  }

  @SuppressWarnings("JavaReflectionMemberAccess")
  private static FileDescriptor wrapFileDescriptor(int fileDescriptor) {
    FileDescriptor result = new FileDescriptor();
    try {
      Field descriptorField;
      try {
        descriptorField = FileDescriptor.class.getDeclaredField("descriptor");
      } catch (NoSuchFieldException e) {
        descriptorField = FileDescriptor.class.getDeclaredField("fd");
      }
      descriptorField.setAccessible(true);
      descriptorField.set(result, fileDescriptor);
    } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException e) {
      Log.wtf(EmulatorDebug.LOG_TAG, "Error accessing FileDescriptor#descriptor private field", e);
      System.exit(1);
    }
    return result;
  }

  private static final int MSG_NEW_INPUT = 1;
  private static final int MSG_PROCESS_EXITED = 4;

  public final String mHandle = UUID.randomUUID().toString();

  private TerminalEmulator mEmulator;

  private final ByteQueue mProcessToTerminalIOQueue = new ByteQueue(4096);
  private final ByteQueue mTerminalToProcessIOQueue = new ByteQueue(4096);
  private final byte[] mUtf8InputBuffer = new byte[5];

  public SessionChangedCallback getSessionChangedCallback() {
    return mChangeCallback;
  }

  private final SessionChangedCallback mChangeCallback;

  private int mShellPid;

  private int mShellExitStatus;

  private int mTerminalFileDescriptor;

  public String mSessionName;

  @SuppressLint("HandlerLeak")
  private final Handler mMainThreadHandler = new Handler() {
    final byte[] mReceiveBuffer = new byte[4 * 1024];

    @Override
    public void handleMessage(Message msg) {
      if (msg.what == MSG_NEW_INPUT && isRunning()) {
        int bytesRead = mProcessToTerminalIOQueue.read(mReceiveBuffer, false);
        if (bytesRead > 0) {
          mEmulator.append(mReceiveBuffer, bytesRead);
          notifyScreenUpdate();
        }
      } else if (msg.what == MSG_PROCESS_EXITED) {
        int exitCode = (Integer) msg.obj;
        cleanupResources(exitCode);
        mChangeCallback.onSessionFinished(TerminalSession.this);

        String exitDescription = getExitDescription(exitCode);
        byte[] bytesToWrite = exitDescription.getBytes(StandardCharsets.UTF_8);
        mEmulator.append(bytesToWrite, bytesToWrite.length);
        notifyScreenUpdate();
      }
    }
  };

  private final String mShellPath;
  private final String mCwd;
  private final String[] mArgs;
  private final String[] mEnv;

  public TerminalSession(String shellPath, String cwd, String[] args, String[] env, SessionChangedCallback changeCallback) {
    mChangeCallback = changeCallback;

    this.mShellPath = shellPath;
    this.mCwd = cwd;
    this.mArgs = args;
    this.mEnv = env;
  }

  public void updateSize(int columns, int rows) {
    if (mEmulator == null) {
      initializeEmulator(columns, rows);
    } else {
      JNI.setPtyWindowSize(mTerminalFileDescriptor, rows, columns);
      mEmulator.resize(columns, rows);
    }
  }

  public String getTitle() {
    return (mEmulator == null) ? null : mEmulator.getTitle();
  }

  public void initializeEmulator(int columns, int rows) {
    mEmulator = new TerminalEmulator(this, columns, rows, 2000);

    int[] processId = new int[1];
    mTerminalFileDescriptor = JNI.createSubprocess(mShellPath, mCwd, mArgs, mEnv, processId, rows, columns);
    mShellPid = processId[0];

    final FileDescriptor terminalFileDescriptorWrapped = wrapFileDescriptor(mTerminalFileDescriptor);

    new Thread("TermSessionInputReader[pid=" + mShellPid + "]") {
      @Override
      public void run() {
        try (InputStream termIn = new FileInputStream(terminalFileDescriptorWrapped)) {
          final byte[] buffer = new byte[4096];
          while (true) {
            int read = termIn.read(buffer);
            if (read == -1) return;
            if (!mProcessToTerminalIOQueue.write(buffer, 0, read)) return;
            mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
          }
        } catch (Exception e) {
        }
      }
    }.start();

    new Thread("TermSessionOutputWriter[pid=" + mShellPid + "]") {
      @Override
      public void run() {
        final byte[] buffer = new byte[4096];
        try (FileOutputStream termOut = new FileOutputStream(terminalFileDescriptorWrapped)) {
          while (true) {
            int bytesToWrite = mTerminalToProcessIOQueue.read(buffer, true);
            if (bytesToWrite == -1) return;
            termOut.write(buffer, 0, bytesToWrite);
          }
        } catch (IOException e) {
        }
      }
    }.start();

    new Thread("TermSessionWaiter[pid=" + mShellPid + "]") {
      @Override
      public void run() {
        int processExitCode = JNI.waitFor(mShellPid);
        mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, processExitCode));
      }
    }.start();
  }

  @Override
  public void write(byte[] data, int offset, int count) {
    if (mShellPid > 0) mTerminalToProcessIOQueue.write(data, offset, count);
  }

  public void writeCodePoint(boolean prependEscape, int codePoint) {
    if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
      throw new IllegalArgumentException("Invalid code point: " + codePoint);
    }

    int bufferPosition = 0;
    if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27;

    if (codePoint <= 0b1111111) {
      mUtf8InputBuffer[bufferPosition++] = (byte) codePoint;
    } else if (codePoint <= 0b11111111111) {
      mUtf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
      mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
    } else if (codePoint <= 0b1111111111111111) {
      mUtf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
      mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
      mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
    } else {
      mUtf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
      mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
      mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
      mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
    }
    write(mUtf8InputBuffer, 0, bufferPosition);
  }

  public TerminalEmulator getEmulator() {
    return mEmulator;
  }

  private void notifyScreenUpdate() {
    mChangeCallback.onTextChanged(this);
  }

  public void reset() {
    mEmulator.reset();
    notifyScreenUpdate();
  }

  public void finishIfRunning() {
    if (isRunning()) {
      try {
        Os.kill(mShellPid, OsConstants.SIGKILL);
      } catch (ErrnoException e) {
        Log.w("neoterm-shell-session",
          "Failed sending SIGKILL: " + e.getMessage());
      }
    }
  }

  protected String getExitDescription(int exitCode) {
    String exitDescription = "\r\n[Process completed";
    if (exitCode > 0) {
      exitDescription += " (code " + exitCode + ")";
    } else if (exitCode < 0) {
      exitDescription += " (signal " + (-exitCode) + ")";
    }
    exitDescription += " - press Enter]";
    return exitDescription;
  }

  private void cleanupResources(int exitStatus) {
    synchronized (this) {
      mShellPid = -1;
      mShellExitStatus = exitStatus;
    }

    mTerminalToProcessIOQueue.close();
    mProcessToTerminalIOQueue.close();
    JNI.close(mTerminalFileDescriptor);
  }

  @Override
  public void titleChanged(String oldTitle, String newTitle) {
    mChangeCallback.onTitleChanged(this);
  }

  public synchronized boolean isRunning() {
    return mShellPid != -1;
  }

  public synchronized int getExitStatus() {
    return mShellExitStatus;
  }

  @Override
  public void clipboardText(String text) {
    mChangeCallback.onClipboardText(this, text);
  }

  @Override
  public void onBell() {
    mChangeCallback.onBell(this);
  }

  @Override
  public void onColorsChanged() {
    mChangeCallback.onColorsChanged(this);
  }

  public int getPid() {
    return mShellPid;
  }

}
