package com.stryker.terminal.framework.database;

import com.stryker.terminal.framework.NeoTermDatabase;
import com.stryker.terminal.framework.NeoTermDatabase;
import com.stryker.terminal.framework.database.annotation.ID;
import com.stryker.terminal.framework.database.annotation.Table;
import com.stryker.terminal.framework.database.bean.TableInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class TableHelper {
  private static final Map<Class<?>, TableInfo> classToTableInfoMap = new HashMap<>();

  public static TableInfo from(Class<?> clazz) {
    TableInfo tableInfo = classToTableInfoMap.get(clazz);
    if (tableInfo != null) {
      return tableInfo;
    }
    tableInfo = new TableInfo();
    Table table = clazz.getAnnotation(Table.class);
    String afterTableCreateMethod = table != null ? table.afterTableCreate() : null;
    if (afterTableCreateMethod != null && afterTableCreateMethod.trim().length() > 0) {
      try {
        Method method = clazz.getDeclaredMethod(afterTableCreateMethod, NeoTermDatabase.class);
        if (method != null && Modifier.isStatic(method.getModifiers())) {
          method.setAccessible(true);
          tableInfo.afterTableCreateMethod = method;
        }
      } catch (Throwable ignored) {
      }
    }
    if (table != null && table.name().trim().length() != 0) {
      tableInfo.tableName = table.name();
    } else {
      tableInfo.tableName = clazz.getName().replace(".", "_");
    }

    Map<Field, DatabaseDataType> fieldEnumMap = new HashMap<>();
    for (Field field : clazz.getDeclaredFields()) {
      field.setAccessible(true);
      if (SQLTypeParser.isIgnore(field)) {
        continue;
      }
      DatabaseDataType dataType = SQLTypeParser.getDataType(field);
      if (dataType != null) {
        fieldEnumMap.put(field, dataType);
      } else {
        throw new IllegalArgumentException("The type of " + field.getName() + " is not supported in database.");
      }
    }

    tableInfo.fieldToDataTypeMap = fieldEnumMap;
    buildPrimaryIDForTableInfo(tableInfo);
    tableInfo.createTableStatement = SQLStatementHelper.createTable(tableInfo);

    synchronized (classToTableInfoMap) {
      classToTableInfoMap.put(clazz, tableInfo);
    }
    return tableInfo;
  }

  private static TableInfo buildPrimaryIDForTableInfo(TableInfo info) {

    Field idField = null;
    ID id;
    for (Field field : info.fieldToDataTypeMap.keySet()) {
      id = field.getAnnotation(ID.class);
      if (id != null) {
        idField = field;
        break;
      }
    }
    if (idField != null) {
      info.fieldToDataTypeMap.remove(idField);
      info.containID = true;
      info.primaryField = idField;
    } else {
      info.containID = false;
      info.primaryField = null;
    }

    return info;
  }

  public static TableInfo findTableInfoByName(String tableName) {

    for (TableInfo tableInfo : classToTableInfoMap.values()) {
      if (tableInfo.tableName.equals(tableName)) {
        return tableInfo;
      }
    }

    return null;
  }

  public static void clearCache() {
    classToTableInfoMap.clear();
  }

}
