package com.stryker.terminal.framework.reflection;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class Reflect {
  private final Object mObject;
  private final boolean isClass;

  private Reflect(Class<?> type) {
    this.mObject = type;
    this.isClass = true;
  }

  private Reflect(Object object) {
    this.mObject = object;
    this.isClass = false;
  }

  public static Reflect on(String name) throws ReflectionException {
    return on(forName(name));
  }

  public static Reflect on(String name, ClassLoader classLoader) throws ReflectionException {
    return on(forName(name, classLoader));
  }

  public static Reflect on(Class<?> clazz) {
    return new Reflect(clazz);
  }

  public static Reflect on(Object object) {
    return new Reflect(object);
  }

  private static Reflect on(Method method, Object receiver, Object... args) throws ReflectionException {
    try {
      makeAccessible(method);

      if (method.getReturnType() == void.class) {
        method.invoke(receiver, args);
        return on(receiver);
      } else {
        return on(method.invoke(receiver, args));
      }
    } catch (Exception e) {
      throw new ReflectionException(e);
    }
  }

  public static <T extends AccessibleObject> T makeAccessible(T accessible) {
    if (accessible == null) {
      return null;
    }

    if (accessible instanceof Member) {
      Member member = (Member) accessible;

      if (Modifier.isPublic(member.getModifiers()) &&
        Modifier.isPublic(member.getDeclaringClass().getModifiers())) {

        return accessible;
      }
    }

    if (!accessible.isAccessible()) {
      accessible.setAccessible(true);
    }

    return accessible;
  }

  private static String property(String string) {
    int length = string.length();

    if (length == 0) {
      return "";
    } else if (length == 1) {
      return string.toLowerCase();
    } else {
      return string.substring(0, 1).toLowerCase() + string.substring(1);
    }
  }

  private static Reflect on(Constructor<?> constructor, Object... args) throws ReflectionException {
    try {
      return on(makeAccessible(constructor).newInstance(args));
    } catch (Exception e) {
      throw new ReflectionException(e);
    }
  }

  private static Object unwrap(Object object) {
    if (object instanceof Reflect) {
      return ((Reflect) object).get();
    }

    return object;
  }

  private static Class<?>[] convertTypes(Object... values) {
    if (values == null) {
      return new Class[0];
    }

    Class<?>[] result = new Class[values.length];

    for (int i = 0; i < values.length; i++) {
      Object value = values[i];
      result[i] = value == null ? NullPointer.class : value.getClass();
    }

    return result;
  }

  private static Class<?> forName(String name) throws ReflectionException {
    try {
      return Class.forName(name);
    } catch (Exception e) {
      throw new ReflectionException(e);
    }
  }

  private static Class<?> forName(String name, ClassLoader classLoader) throws ReflectionException {
    try {
      return Class.forName(name, true, classLoader);
    } catch (Exception e) {
      throw new ReflectionException(e);
    }
  }

  private static Class<?> wrapClassType(Class<?> type) {
    if (type == null) {
      return null;
    } else if (type.isPrimitive()) {
      if (boolean.class == type) {
        return Boolean.class;
      } else if (int.class == type) {
        return Integer.class;
      } else if (long.class == type) {
        return Long.class;
      } else if (short.class == type) {
        return Short.class;
      } else if (byte.class == type) {
        return Byte.class;
      } else if (double.class == type) {
        return Double.class;
      } else if (float.class == type) {
        return Float.class;
      } else if (char.class == type) {
        return Character.class;
      } else if (void.class == type) {
        return Void.class;
      }
    }

    return type;
  }

  @SuppressWarnings("unchecked")
  public <T> T get() {
    return (T) mObject;
  }

  public Reflect set(String name, Object value) throws ReflectionException {
    try {
      Field field = lookupField(name);
      field.setAccessible(true);
      field.set(mObject, unwrap(value));
      return this;
    } catch (Exception e) {
      throw new ReflectionException(e);
    }
  }

  public <T> T get(String name) throws ReflectionException {
    return field(name).get();
  }

  public Reflect field(String name) throws ReflectionException {
    try {
      Field field = lookupField(name);
      return on(field.get(mObject));
    } catch (Exception e) {
      throw new ReflectionException(e);
    }
  }

  private Field lookupField(String name) throws ReflectionException {
    Class<?> type = type();

    try {
      return type.getField(name);
    }

    catch (NoSuchFieldException e) {
      do {
        try {
          return makeAccessible(type.getDeclaredField(name));
        } catch (NoSuchFieldException ignore) {
        }

        type = type.getSuperclass();
      }
      while (type != null);

      throw new ReflectionException(e);
    }
  }

  public Map<String, Reflect> fields() {
    Map<String, Reflect> result = new LinkedHashMap<String, Reflect>();
    Class<?> type = type();

    do {
      for (Field field : type.getDeclaredFields()) {
        if (!isClass ^ Modifier.isStatic(field.getModifiers())) {
          String name = field.getName();

          if (!result.containsKey(name))
            result.put(name, field(name));
        }
      }

      type = type.getSuperclass();
    }
    while (type != null);

    return result;
  }

  public Reflect call(String name) throws ReflectionException {
    return call(name, new Object[0]);
  }

  public Reflect call(String name, Object... args) throws ReflectionException {
    Class<?>[] types = convertTypes(args);

    try {
      Method method = exactMethod(name, types);
      return on(method, mObject, args);
    } catch (NoSuchMethodException e) {
      try {
        Method method = lookupSimilarMethod(name, types);
        return on(method, mObject, args);
      } catch (NoSuchMethodException e1) {
        throw new ReflectionException(e1);
      }
    }
  }

  private Method exactMethod(String name, Class<?>[] types) throws NoSuchMethodException {
    Class<?> type = type();

    try {
      return type.getMethod(name, types);
    } catch (NoSuchMethodException e) {
      do {
        try {
          return type.getDeclaredMethod(name, types);
        } catch (NoSuchMethodException ignore) {
        }

        type = type.getSuperclass();
      }
      while (type != null);

      throw new NoSuchMethodException();
    }
  }

  private Method lookupSimilarMethod(String name, Class<?>[] types) throws NoSuchMethodException {
    Class<?> type = type();

    for (Method method : type.getMethods()) {
      if (isSignatureSimilar(method, name, types)) {
        return method;
      }
    }

    do {
      for (Method method : type.getDeclaredMethods()) {
        if (isSignatureSimilar(method, name, types)) {
          return method;
        }
      }

      type = type.getSuperclass();
    }
    while (type != null);

    throw new NoSuchMethodException("No similar method " + name + " with params " + Arrays.toString(types) + " could be found on type " + type() + ".");
  }

  private boolean isSignatureSimilar(Method possiblyMatchingMethod,
                                     String wantedMethodName,
                                     Class<?>[] wantedParamTypes) {
    return possiblyMatchingMethod.getName().equals(wantedMethodName)
      && match(possiblyMatchingMethod.getParameterTypes(), wantedParamTypes);
  }

  public Reflect create() throws ReflectionException {
    return create(new Object[0]);
  }

  public Reflect create(Object... args) throws ReflectionException {
    Class<?>[] types = convertTypes(args);


    try {
      Constructor<?> constructor = type().getDeclaredConstructor(types);
      return on(constructor, args);
    } catch (NoSuchMethodException e) {
      for (Constructor<?> constructor : type().getDeclaredConstructors()) {
        if (match(constructor.getParameterTypes(), types)) {
          return on(constructor, args);
        }
      }

      throw new ReflectionException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public <P> P as(Class<P> proxyType) {
    final boolean isMap = (mObject instanceof Map);
    final InvocationHandler handler = (proxy, method, args) -> {
      String name = method.getName();
      try {
        return on(mObject).call(name, args).get();
      } catch (ReflectionException e) {
        if (isMap) {
          Map<String, Object> map = (Map<String, Object>) mObject;
          int length = (args == null ? 0 : args.length);

          if (length == 0 && name.startsWith("get")) {
            return map.get(property(name.substring(3)));
          } else if (length == 0 && name.startsWith("is")) {
            return map.get(property(name.substring(2)));
          } else if (length == 1 && name.startsWith("set")) {
            map.put(property(name.substring(3)), args[0]);
            return null;
          }
        }

        throw e;
      }
    };

    return (P) Proxy.newProxyInstance(proxyType.getClassLoader(),
      new Class[]{proxyType}, handler);
  }

  private boolean match(Class<?>[] declaredTypes, Class<?>[] actualTypes) {
    if (declaredTypes.length == actualTypes.length) {
      for (int i = 0; i < actualTypes.length; i++) {
        if (actualTypes[i] == NullPointer.class) {
          continue;
        }

        if (wrapClassType(declaredTypes[i]).isAssignableFrom(wrapClassType(actualTypes[i]))) {
          continue;
        }
        return false;
      }

      return true;
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return mObject.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Reflect) {
      return mObject.equals(((Reflect) obj).get());
    }

    return false;
  }

  @Override
  public String toString() {
    return mObject.toString();
  }

  public Class<?> type() {
    if (isClass) {
      return (Class<?>) mObject;
    } else {
      return mObject.getClass();
    }
  }
}
