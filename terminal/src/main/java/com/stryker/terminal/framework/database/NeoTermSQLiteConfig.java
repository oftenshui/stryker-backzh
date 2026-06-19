package com.stryker.terminal.framework.database;

import java.io.Serializable;

public class NeoTermSQLiteConfig implements Serializable {

  private static final long serialVersionUID = -4069725570156436316L;
  public static String DEFAULT_DB_NAME = "we_like.db";
  public static NeoTermSQLiteConfig DEFAULT_CONFIG = new NeoTermSQLiteConfig();

  public boolean debugMode = false;
  private String dbName = DEFAULT_DB_NAME;
  private OnDatabaseUpgradedListener onDatabaseUpgradedListener;
  private boolean defaultDropAllTables = false;
  private String saveDir;
  private int dbVersion = 1;

  public String getDatabaseName() {
    return dbName;
  }

  public void setDatabaseName(String dbName) {
    this.dbName = dbName;
  }

  public OnDatabaseUpgradedListener getOnDatabaseUpgradedListener() {
    return onDatabaseUpgradedListener;
  }

  public void setOnDatabaseUpgradedListener(OnDatabaseUpgradedListener onDatabaseUpgradedListener) {
    this.onDatabaseUpgradedListener = onDatabaseUpgradedListener;
  }

  public String getSaveDir() {
    return saveDir;
  }

  public void setSaveDir(String saveDir) {
    this.saveDir = saveDir;
  }

  public int getDatabaseVersion() {
    return dbVersion;
  }

  public void setDatabaseVersion(int dbVersion) {
    this.dbVersion = dbVersion;
  }

  public boolean isDefaultDropAllTables() {
    return defaultDropAllTables;
  }

  public void setDefaultDropAllTables(boolean defaultDropAllTables) {
    this.defaultDropAllTables = defaultDropAllTables;
  }
}
