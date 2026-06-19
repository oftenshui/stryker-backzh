package com.stryker.terminal.framework.database;

import android.database.Cursor;

import java.lang.reflect.Field;

public class ValueHelper {

  public static void setKeyValue(Cursor cursor, Object object, Field field, DatabaseDataType dataType, int index) {
    switch (dataType) {
      case INTEGER:
        try {
          field.set(object, cursor.getInt(index));
        } catch (Throwable e) {
          try {
            field.set(object, cursor.getInt(index) != 0);
          } catch (IllegalAccessException ignored) {
          }
        }
        break;
      case TEXT:
        try {
          field.set(object, cursor.getString(index));
        } catch (IllegalAccessException e) {
        }
        break;
      case FLOAT:
        try {
          field.set(object, cursor.getFloat(index));
        } catch (IllegalAccessException e) {
        }
        break;
      case BIGINT:
        try {
          field.set(object, cursor.getLong(index));
        } catch (IllegalAccessException e) {
        }
        break;
      case DOUBLE:
        try {
          field.set(object, cursor.getDouble(index));
        } catch (IllegalAccessException e) {
        }
        break;

    }
  }

  public static String valueToString(DatabaseDataType dataType, Field field, Object o) throws IllegalAccessException {
    switch (dataType) {
      case INTEGER:
        Object f = field.get(o);
        if (f instanceof Boolean) {
          return String.valueOf(((boolean) field.get(o)) ? 1 : 0);
        } else {
          return String.valueOf((int) field.get(o));
        }
      case TEXT:
        return "\"" + field.get(o) + "" + "\"";
      case DOUBLE:
        return String.valueOf((double) field.get(o));
      case FLOAT:
        return String.valueOf((float) field.get(o));
      case BIGINT:
        return String.valueOf((long) field.get(o));
    }
    return null;
  }

  public static String valueToString(DatabaseDataType dataType, Object o) {
    switch (dataType) {
      case INTEGER:
        if (o instanceof Boolean) {
          return ((boolean) o) ? "1" : "0";
        } else {
          return String.valueOf((int) o);
        }
      case TEXT:
        return "\"" + o + "\"";
      case DOUBLE:
        return String.valueOf((double) o);
      case FLOAT:
        return String.valueOf((float) o);
      case BIGINT:
        return String.valueOf((long) o);
    }
    return null;
  }
}
