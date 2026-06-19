package com.stryker.terminal.setup;

import java.io.IOException;
import java.io.InputStream;

public interface SourceConnection {
  InputStream getInputStream() throws IOException;
  int getSize();
  void close();
}
