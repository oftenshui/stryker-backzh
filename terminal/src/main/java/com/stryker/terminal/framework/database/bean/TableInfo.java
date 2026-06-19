package com.stryker.terminal.framework.database.bean;


import com.stryker.terminal.framework.database.DatabaseDataType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class TableInfo {

  public boolean containID;
  public Field primaryField;

  public String tableName;

  public Map<Field, DatabaseDataType> fieldToDataTypeMap;

  public String createTableStatement;

  public boolean isCreate = false;

  public Method afterTableCreateMethod;

}
