package com.stryker.terminal.backend;

final class JNI {

  static {
    System.loadLibrary("terminal");
  }

  public static native int createSubprocess(String cmd, String cwd, String[] args, String[] envVars, int[] processId, int rows, int columns);

  public static native void setPtyWindowSize(int fd, int rows, int cols);

  public static native int waitFor(int processId);

  public static native void close(int fileDescriptor);

}
