package com.stryker.terminal;

import android.content.Context;

public class NeoAccelerometerReader extends AccelerometerReader {
  public NeoAccelerometerReader(Context context) {
    super(context);
  }

  public static void setGyroInvertedOrientation(boolean invertedOrientation) {
    gyro.invertedOrientation = invertedOrientation;
  }
}
