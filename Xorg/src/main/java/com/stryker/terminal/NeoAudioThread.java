package com.stryker.terminal;

import com.stryker.terminal.xorg.NeoXorgViewClient;

public class NeoAudioThread extends AudioThread {
  public NeoAudioThread(NeoXorgViewClient client) {
    super(client);
  }
}
