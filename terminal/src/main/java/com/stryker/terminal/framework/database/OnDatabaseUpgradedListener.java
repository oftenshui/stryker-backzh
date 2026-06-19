package com.stryker.terminal.framework.database;

import android.database.sqlite.SQLiteDatabase;

public interface OnDatabaseUpgradedListener {
  void onDatabaseUpgraded(SQLiteDatabase db, int oldVersion, int newVersion);
}
