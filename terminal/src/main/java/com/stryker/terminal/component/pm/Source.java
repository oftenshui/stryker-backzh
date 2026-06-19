package com.stryker.terminal.component.pm;

import com.stryker.terminal.framework.database.annotation.ID;
import com.stryker.terminal.framework.database.annotation.Table;
import com.stryker.terminal.framework.database.annotation.ID;
import com.stryker.terminal.framework.database.annotation.Table;

@Table
public class Source {
  @ID(autoIncrement = true)
  private int id;

  public String url;

  public String repo;

  public boolean enabled;

  public Source() {
  }

  public Source(String url, String repo, boolean enabled) {
    this.url = url;
    this.repo = repo;
    this.enabled = enabled;
  }
}
