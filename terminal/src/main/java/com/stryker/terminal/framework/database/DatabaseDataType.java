package com.stryker.terminal.framework.database;

public enum DatabaseDataType {
  INTEGER,
  TEXT,
  FLOAT,
  BIGINT,
  DOUBLE;

  boolean nullable = true;

  public DatabaseDataType nullable(boolean nullable) {
    this.nullable = nullable;
    return this;
  }

}
