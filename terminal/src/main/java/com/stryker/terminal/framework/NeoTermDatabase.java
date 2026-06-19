package com.stryker.terminal.framework;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.stryker.terminal.App;
import com.stryker.terminal.framework.database.*;

import com.stryker.terminal.framework.database.DatabaseDataType;
import com.stryker.terminal.framework.database.NeoTermSQLiteConfig;
import com.stryker.terminal.framework.database.OnDatabaseUpgradedListener;
import com.stryker.terminal.framework.database.SQLStatementHelper;
import com.stryker.terminal.framework.database.SQLTypeParser;
import com.stryker.terminal.framework.database.TableHelper;
import com.stryker.terminal.framework.database.ValueHelper;
import com.stryker.terminal.framework.database.bean.TableInfo;
import com.stryker.terminal.framework.reflection.Reflect;
import com.stryker.terminal.utils.NLog;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class NeoTermDatabase {

  private static final Map<String, NeoTermDatabase> DAO_MAP = new HashMap<>();

  private NeoTermSQLiteConfig neoTermSQLiteConfig;
  private SQLiteDatabase db;

  private NeoTermDatabase(NeoTermSQLiteConfig config) {

    this.neoTermSQLiteConfig = config;
    String saveDir = config.getSaveDir();
    if (saveDir != null
      && saveDir.trim().length() > 0) {
      this.db = createDataBaseFileOnSDCard(saveDir,
        config.getDatabaseName());
    } else {
      this.db = new SQLiteDataBaseHelper(App.Companion.get()
        .getApplicationContext()
        .getApplicationContext(), config)
        .getWritableDatabase();
    }

  }

  public static NeoTermDatabase instance(NeoTermSQLiteConfig config) {
    if (config.getDatabaseName() == null) {
      throw new IllegalArgumentException("DBName is null in SqLiteConfig.");
    }
    NeoTermDatabase dao = DAO_MAP.get(config.getDatabaseName());
    if (dao == null) {
      dao = new NeoTermDatabase(config);
      synchronized (DAO_MAP) {
        DAO_MAP.put(config.getDatabaseName(), dao);
      }
    } else {
      dao.applyConfig(config);
    }

    return dao;
  }

  public static NeoTermDatabase instance() {
    return instance(NeoTermSQLiteConfig.DEFAULT_CONFIG);
  }

  public static NeoTermDatabase instance(String dbName) {
    NeoTermSQLiteConfig config = new NeoTermSQLiteConfig();
    config.setDatabaseName(dbName);
    return instance(config);
  }

  public static NeoTermDatabase instance(int dbVersion) {
    NeoTermSQLiteConfig config = new NeoTermSQLiteConfig();
    config.setDatabaseVersion(dbVersion);
    return instance(config);
  }

  public static NeoTermDatabase instance(OnDatabaseUpgradedListener listener) {
    NeoTermSQLiteConfig config = new NeoTermSQLiteConfig();
    config.setOnDatabaseUpgradedListener(listener);
    return instance(config);
  }

  public static NeoTermDatabase instance(String dbName, int dbVersion) {
    NeoTermSQLiteConfig config = new NeoTermSQLiteConfig();
    config.setDatabaseName(dbName);
    config.setDatabaseVersion(dbVersion);
    return instance(config);
  }

  public static NeoTermDatabase instance(String dbName, int dbVersion, OnDatabaseUpgradedListener listener) {
    NeoTermSQLiteConfig config = new NeoTermSQLiteConfig();
    config.setDatabaseName(dbName);
    config.setDatabaseVersion(dbVersion);
    config.setOnDatabaseUpgradedListener(listener);
    return instance(config);
  }

  private void applyConfig(NeoTermSQLiteConfig config) {
    this.neoTermSQLiteConfig.debugMode = config.debugMode;
    this.neoTermSQLiteConfig.setOnDatabaseUpgradedListener(config.getOnDatabaseUpgradedListener());
  }

  public void release() {
    DAO_MAP.clear();
    if (neoTermSQLiteConfig.debugMode) {
      NLog.INSTANCE.d("缓存的DAO已经全部清除,将不占用内存.");
    }
  }


  private SQLiteDatabase createDataBaseFileOnSDCard(String sdcardPath,
                                                    String dbFileName) {
    File dbFile = new File(sdcardPath, dbFileName);
    if (!dbFile.exists()) {
      try {
        if (dbFile.createNewFile()) {
          return SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        }
      } catch (IOException e) {
        throw new RuntimeException("无法在 " + dbFile.getAbsolutePath() + "创建DB文件.");
      }
    } else {
      return SQLiteDatabase.openOrCreateDatabase(dbFile, null);
    }
    return null;
  }

  private void createTableIfNeed(Class<?> clazz) {
    TableInfo tableInfo = TableHelper.from(clazz);
    if (tableInfo.isCreate) {
      return;
    }
    if (!isTableExist(tableInfo)) {
      String sql = SQLStatementHelper.createTable(tableInfo);
      if (neoTermSQLiteConfig.debugMode) {
        NLog.INSTANCE.w(sql);
      }
      db.execSQL(sql);
      Method afterTableCreateMethod = tableInfo.afterTableCreateMethod;
      if (afterTableCreateMethod != null) {
        try {
          afterTableCreateMethod.invoke(null, this);
        } catch (Throwable ignore) {
          ignore.printStackTrace();
        }
      }
    }
  }

  private boolean isTableExist(TableInfo table) {
    String sql = "SELECT COUNT(*) AS c FROM sqlite_master WHERE type ='table' AND name ='"
      + table.tableName + "' ";
    try (Cursor cursor = db.rawQuery(sql, null)) {
      if (cursor != null && cursor.moveToNext()) {
        int count = cursor.getInt(0);
        if (count > 0) {
          return true;
        }
      }
    } catch (Throwable ignore) {
      ignore.printStackTrace();
    }

    return false;
  }

  public void dropAllTable() {
    try (Cursor cursor = db.rawQuery(
      "SELECT name FROM sqlite_master WHERE type ='table'", null)) {
      if (cursor != null) {
        cursor.moveToFirst();
        while (cursor.moveToNext()) {
          try {
            dropTable(cursor.getString(0));
          } catch (SQLException ignore) {
            ignore.printStackTrace();
          }
        }
      }
    }
  }

  public int tableCount() {
    try (Cursor cursor = db.rawQuery(
      "SELECT name FROM sqlite_master WHERE type ='table'", null)) {
      return cursor == null ? 0 : cursor.getCount();
    }
  }

  public List<String> getTableList() {
    try (Cursor cursor = db.rawQuery(
      "SELECT name FROM sqlite_master WHERE type ='table'", null)) {
      List<String> tableList = new ArrayList<>();
      if (cursor != null) {
        cursor.moveToFirst();
        while (cursor.moveToNext()) {
          tableList.add(cursor.getString(0));
        }
      }
      return tableList;
    }
  }

  public void dropTable(Class<?> beanClass) {
    TableInfo tableInfo = TableHelper.from(beanClass);
    dropTable(tableInfo.tableName);
    tableInfo.isCreate = false;
  }

  public void dropTable(String tableName) {
    String statement = "DROP TABLE IF EXISTS " + tableName;
    if (neoTermSQLiteConfig.debugMode) {
      NLog.INSTANCE.w(statement);
    }
    db.execSQL(statement);
    TableInfo tableInfo = TableHelper.findTableInfoByName(tableName);
    if (tableInfo != null) {
      tableInfo.isCreate = false;
    }
  }

  public <T> NeoTermDatabase saveBean(T bean) {
    createTableIfNeed(bean.getClass());
    String statement = SQLStatementHelper.insertIntoTable(bean);
    if (neoTermSQLiteConfig.debugMode) {
      NLog.INSTANCE.w(statement);
    }
    db.execSQL(statement);
    return this;

  }

  public NeoTermDatabase saveBeans(Object[] beans) {
    for (Object o : beans) {
      saveBean(o);
    }

    return this;
  }

  public <T> NeoTermDatabase saveBeans(List<T> beans) {
    for (Object o : beans) {
      saveBean(o);
    }

    return this;
  }

  public <T> List<T> findAll(Class<?> clazz) {
    createTableIfNeed(clazz);
    TableInfo tableInfo = TableHelper.from(clazz);
    String statement = SQLStatementHelper.selectTable(tableInfo.tableName);
    if (neoTermSQLiteConfig.debugMode) {
      NLog.INSTANCE.w(statement);
    }
    List<T> list = new ArrayList<T>();
    try (Cursor cursor = db.rawQuery(statement, null)) {
      if (cursor == null) {
        return Collections.emptyList();
      }

      while (cursor.moveToNext()) {
        T object = Reflect.on(clazz).create().get();

        if (tableInfo.containID) {
          DatabaseDataType dataType = SQLTypeParser.getDataType(tableInfo.primaryField);
          String idFieldName = tableInfo.primaryField.getName();
          ValueHelper.setKeyValue(cursor, object, tableInfo.primaryField, dataType, cursor.getColumnIndex(idFieldName));
        }

        for (Field field : tableInfo.fieldToDataTypeMap.keySet()) {
          DatabaseDataType dataType = tableInfo.fieldToDataTypeMap.get(field);
          ValueHelper.setKeyValue(cursor, object, field, dataType, cursor.getColumnIndex(field.getName()));
        }
        list.add(object);
      }
      return list;
    }
  }

  public <T> List<T> findBeanByWhere(Class<?> clazz, String where) {
    createTableIfNeed(clazz);
    TableInfo tableInfo = TableHelper.from(clazz);
    String statement = SQLStatementHelper.findByWhere(tableInfo, where);
    if (neoTermSQLiteConfig.debugMode) {
      NLog.INSTANCE.w(statement);
    }
    List<T> list = new ArrayList<>();
    try (Cursor cursor = db.rawQuery(statement, null)) {
      if (cursor == null) {
        return Collections.emptyList();
      }

      while (cursor.moveToNext()) {
        T object = Reflect.on(clazz).create().get();
        if (tableInfo.containID) {
          DatabaseDataType dataType = SQLTypeParser.getDataType(tableInfo.primaryField);
          String idFieldName = tableInfo.primaryField.getName();
          ValueHelper.setKeyValue(cursor, object, tableInfo.primaryField, dataType, cursor.getColumnIndex(idFieldName));
        }
        for (Field field : tableInfo.fieldToDataTypeMap.keySet()) {
          DatabaseDataType dataType = tableInfo.fieldToDataTypeMap.get(field);
          ValueHelper.setKeyValue(cursor, object, field, dataType, cursor.getColumnIndex(field.getName()));
        }
        list.add(object);
      }
      return list;
    }
  }

  public <T> T findOneBeanByWhere(Class<?> clazz, String where) {
    List<T> list = findBeanByWhere(clazz, where);
    if (!list.isEmpty()) {
      return list.get(0);
    }
    return null;
  }

  public NeoTermDatabase deleteBeanByWhere(Class<?> clazz, String where) {
    createTableIfNeed(clazz);
    TableInfo tableInfo = TableHelper.from(clazz);
    String statement = SQLStatementHelper.deleteByWhere(tableInfo, where);
    if (neoTermSQLiteConfig.debugMode) {
      NLog.INSTANCE.w(statement);
    }
    try {
      db.execSQL(statement);
    } catch (SQLException ignore) {
      ignore.printStackTrace();
    }

    return this;
  }

  public NeoTermDatabase deleteBeanByID(Class<?> tableClass, Object id) {
    createTableIfNeed(tableClass);
    TableInfo tableInfo = TableHelper.from(tableClass);
    DatabaseDataType dataType = SQLTypeParser.getDataType(id.getClass());
    if (dataType != null && tableInfo.primaryField != null) {
      boolean match = SQLTypeParser.matchType(tableInfo.primaryField, dataType);
      if (!match) {
        throw new IllegalArgumentException("类型 " + id.getClass().getName() + " 不是主键的类型,主键的类型应该为 " + tableInfo.primaryField.getType().getName());
      }
    }
    String idValue = ValueHelper.valueToString(dataType, id);
    String statement = SQLStatementHelper.deleteByWhere(tableInfo, tableInfo.primaryField == null ? "_id" : tableInfo.primaryField.getName() + " = " + idValue);
    if (neoTermSQLiteConfig.debugMode) {
      NLog.INSTANCE.w(statement);
    }

    try {
      db.execSQL(statement);
    } catch (SQLException ignore) {
      ignore.printStackTrace();
    }
    return this;

  }

  public NeoTermDatabase updateByWhere(Class<?> tableClass, String where, Object bean) {
    createTableIfNeed(tableClass);
    TableInfo tableInfo = TableHelper.from(tableClass);
    String statement = SQLStatementHelper.updateByWhere(tableInfo, bean, where);
    if (neoTermSQLiteConfig.debugMode) {
      NLog.INSTANCE.d(statement);
    }
    db.execSQL(statement);
    return this;
  }

  public NeoTermDatabase updateByID(Class<?> tableClass, Object id, Object bean) {
    createTableIfNeed(tableClass);
    TableInfo tableInfo = TableHelper.from(tableClass);
    StringBuilder subStatement = new StringBuilder();
    if (tableInfo.containID) {
      subStatement.append(tableInfo.primaryField.getName()).append(" = ").append(ValueHelper.valueToString(SQLTypeParser.getDataType(tableInfo.primaryField), id));
    } else {
      subStatement.append("_id = ").append((int) id);
    }
    updateByWhere(tableClass, subStatement.toString(), bean);

    return this;
  }

  public <T> T findBeanByID(Class<?> tableClass, Object id) {
    createTableIfNeed(tableClass);
    TableInfo tableInfo = TableHelper.from(tableClass);
    DatabaseDataType dataType = SQLTypeParser.getDataType(id.getClass());
    if (dataType == null) {
      return null;
    }
    boolean match = SQLTypeParser.matchType(tableInfo.primaryField, dataType) || tableInfo.primaryField == null;
    if (!match) {
      throw new IllegalArgumentException("Type " + id.getClass().getName() + " is not the primary key, expecting " + tableInfo.primaryField.getType().getName());
    }
    String idValue = ValueHelper.valueToString(dataType, id);
    String statement = SQLStatementHelper.findByWhere(tableInfo, tableInfo.primaryField == null ? "_id" : tableInfo.primaryField.getName() + " = " + idValue);
    if (neoTermSQLiteConfig.debugMode) {
      NLog.INSTANCE.w(statement);
    }

    try (Cursor cursor = db.rawQuery(statement, null)) {
      if (cursor != null && cursor.getCount() > 0) {
        cursor.moveToFirst();
        T bean = Reflect.on(tableClass).create().get();
        for (Field field : tableInfo.fieldToDataTypeMap.keySet()) {
          DatabaseDataType fieldType = tableInfo.fieldToDataTypeMap.get(field);
          ValueHelper.setKeyValue(cursor, bean, field, fieldType, cursor.getColumnIndex(field.getName()));
        }
        try {
          Reflect.on(bean).set(tableInfo.containID ? tableInfo.primaryField.getName() : "_id", id);
        } catch (Throwable ignore) {
        }
        return bean;
      }
      return null;
    }
  }

  public void vacuum() {
    db.execSQL("VACUUM");
  }

  public void destroy() {
    DAO_MAP.remove(this);
    this.neoTermSQLiteConfig = null;
    this.db = null;
  }

  public SQLiteDatabase getDatabase() {
    return db;
  }

  private class SQLiteDataBaseHelper extends SQLiteOpenHelper {
    private final OnDatabaseUpgradedListener onDatabaseUpgradedListener;
    private final boolean defaultDropAllTables;

    public SQLiteDataBaseHelper(Context context, NeoTermSQLiteConfig config) {
      super(context, config.getDatabaseName(), null, config.getDatabaseVersion());
      this.onDatabaseUpgradedListener = config.getOnDatabaseUpgradedListener();
      this.defaultDropAllTables = config.isDefaultDropAllTables();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      if (onDatabaseUpgradedListener != null) {
        onDatabaseUpgradedListener.onDatabaseUpgraded(db, oldVersion, newVersion);

      } else if (defaultDropAllTables) {
        dropAllTable();
      }
    }
  }
}
