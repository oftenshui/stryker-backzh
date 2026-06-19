package com.stryker.terminal.framework.database;

import com.stryker.terminal.framework.database.annotation.Ignore;
import com.stryker.terminal.framework.database.annotation.NotNull;

import java.lang.reflect.Field;

public class SQLTypeParser {
  public static DatabaseDataType getDataType(Field field) {
    Class<?> clazz = field.getType();
    if (clazz == (String.class)) {
      return DatabaseDataType.TEXT.nullable((field.getAnnotation(NotNull.class) == null));
    } else if (clazz == (int.class) || clazz == (Integer.class)) {
      return DatabaseDataType.INTEGER.nullable((field.getAnnotation(NotNull.class) == null));
    } else if (clazz == (float.class) || clazz == (Float.class)) {
      return DatabaseDataType.FLOAT.nullable((field.getAnnotation(NotNull.class) == null));
    } else if (clazz == (long.class) || clazz == (Long.class)) {
      return DatabaseDataType.BIGINT.nullable((field.getAnnotation(NotNull.class) == null));
    } else if (clazz == (double.class) || clazz == (Double.class)) {
      return DatabaseDataType.DOUBLE.nullable((field.getAnnotation(NotNull.class) == null));
    } else if (clazz == (boolean.class) || clazz == (Boolean.class)) {
      return DatabaseDataType.INTEGER.nullable((field.getAnnotation(NotNull.class) == null));
    }
    return null;
  }

  public static DatabaseDataType getDataType(Class<?> clazz) {
    if (clazz == (String.class)) {
      return DatabaseDataType.TEXT;
    } else if (clazz == (int.class) || clazz == (Integer.class)) {
      return DatabaseDataType.INTEGER;
    } else if (clazz == (float.class) || clazz == (Float.class)) {
      return DatabaseDataType.FLOAT;
    } else if (clazz == (long.class) || clazz == (Long.class)) {
      return DatabaseDataType.BIGINT;
    } else if (clazz == (double.class) || clazz == (Double.class)) {
      return DatabaseDataType.DOUBLE;
    } else if (clazz == (boolean.class) || clazz == (Boolean.class)) {
      return DatabaseDataType.INTEGER;
    }
    return null;
  }

  public static boolean matchType(Field field, DatabaseDataType dataType) {
    DatabaseDataType fieldDataType = getDataType(field.getType());

    return dataType != null && fieldDataType == (dataType);
  }

  public static boolean isIgnore(Field field) {
    return field.getAnnotation(Ignore.class) != null;
  }
}
