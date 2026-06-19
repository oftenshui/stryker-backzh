package com.stryker.terminal;

import com.stryker.terminal.xorg.NeoXorgViewClient;

public class NeoXorgSettings {
  public static void init(NeoXorgViewClient client) {
    Settings.Load(client);
  }
}
