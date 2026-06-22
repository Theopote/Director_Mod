package com.director.ui.properties;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 属性面板反射读写辅助：getter 优先、字段回退。
 * <p>
 * 仅捕获 {@link ReflectiveOperationException}；被调用方法抛出的 {@link RuntimeException}
 * 会原样传播，避免吞掉编程错误。
 */
public final class PropertyReflection {

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyReflection.class);

    private PropertyReflection() {
    }

    public static String getString(Object obj, String fieldName, String getterName, String defaultValue) {
        Object result = readProperty(obj, fieldName, getterName);
        return result != null ? result.toString() : defaultValue;
    }

    public static boolean setString(Object obj, String fieldName, String setterName, String value) {
        return writeProperty(obj, fieldName, setterName, new Class<?>[]{String.class}, new Object[]{value});
    }

    public static boolean getBoolean(Object obj, String fieldName, String getterName, boolean defaultValue) {
        Object result = readProperty(obj, fieldName, getterName);
        return result instanceof Boolean b ? b : defaultValue;
    }

    public static boolean setBoolean(Object obj, String fieldName, String setterName, boolean value) {
        return writeProperty(obj, fieldName, setterName, new Class<?>[]{boolean.class}, new Object[]{value});
    }

    public static int getInt(Object obj, String fieldName, String getterName, int defaultValue) {
        Object result = readProperty(obj, fieldName, getterName);
        return result instanceof Integer i ? i : defaultValue;
    }

    public static boolean setInt(Object obj, String fieldName, String setterName, int value) {
        return writeProperty(obj, fieldName, setterName, new Class<?>[]{int.class}, new Object[]{value});
    }

    public static float getFloat(Object obj, String fieldName, String getterName, float defaultValue) {
        Object result = readProperty(obj, fieldName, getterName);
        if (result instanceof Float f) {
            return f;
        }
        if (result instanceof Double d) {
            return d.floatValue();
        }
        return defaultValue;
    }

    public static boolean setFloat(Object obj, String fieldName, String setterName, float value) {
        return writeProperty(obj, fieldName, setterName, new Class<?>[]{float.class}, new Object[]{value});
    }

    @Nullable
    public static Object readProperty(Object obj, String fieldName, String getterName) {
        Object viaGetter = invokeGetter(obj, getterName);
        if (viaGetter != null || getterExists(obj, getterName)) {
            return viaGetter;
        }
        return readField(obj, fieldName);
    }

    @Nullable
    public static float[] getFloatArray(Object obj, String fieldName, String getterName) {
        Object result = readProperty(obj, fieldName, getterName);
        return result instanceof float[] arr ? arr : null;
    }

    public static boolean setFloatArray(Object obj, String fieldName, String setterName, float[] value) {
        return writeProperty(obj, fieldName, setterName, new Class<?>[]{float[].class}, new Object[]{value});
    }

    public static long getLong(Object obj, String fieldName, String getterName, long defaultValue) {
        Object result = readProperty(obj, fieldName, getterName);
        if (result instanceof Long l) {
            return l;
        }
        if (result instanceof Integer i) {
            return i.longValue();
        }
        return defaultValue;
    }

    public static boolean invokeVoid(Object obj, String methodName) {
        try {
            Method method = obj.getClass().getMethod(methodName);
            method.invoke(obj);
            return true;
        } catch (NoSuchMethodException e) {
            LOGGER.debug("[PropertyReflection] 方法不存在: {}.{}", obj.getClass().getSimpleName(), methodName);
            return false;
        } catch (InvocationTargetException e) {
            throwUnchecked(e);
            return false;
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("[PropertyReflection] 调用失败: {}.{}", obj.getClass().getSimpleName(), methodName);
            return false;
        }
    }

    @Nullable
    private static Object invokeGetter(Object obj, String getterName) {
        try {
            Method method = obj.getClass().getMethod(getterName);
            return method.invoke(obj);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (InvocationTargetException e) {
            throwUnchecked(e);
            return null;
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("[PropertyReflection] getter 失败: {}.{}", obj.getClass().getSimpleName(), getterName);
            return null;
        }
    }

    private static boolean getterExists(Object obj, String getterName) {
        try {
            obj.getClass().getMethod(getterName);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Nullable
    private static Object readField(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (NoSuchFieldException e) {
            LOGGER.debug("[PropertyReflection] 字段不存在: {}.{}", obj.getClass().getSimpleName(), fieldName);
            return null;
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("[PropertyReflection] 读字段失败: {}.{}", obj.getClass().getSimpleName(), fieldName);
            return null;
        }
    }

    private static boolean writeProperty(
            Object obj,
            String fieldName,
            String setterName,
            Class<?>[] paramTypes,
            Object[] args) {
        try {
            Method method = obj.getClass().getMethod(setterName, paramTypes);
            method.invoke(obj, args);
            return true;
        } catch (NoSuchMethodException unused) {
            // fall through to field
        } catch (InvocationTargetException e) {
            throwUnchecked(e);
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("[PropertyReflection] setter 失败: {}.{}", obj.getClass().getSimpleName(), setterName);
        }

        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, args[0]);
            return true;
        } catch (NoSuchFieldException e) {
            LOGGER.debug("[PropertyReflection] 字段不存在: {}.{}", obj.getClass().getSimpleName(), fieldName);
            return false;
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("[PropertyReflection] 写字段失败: {}.{}", obj.getClass().getSimpleName(), fieldName);
            return false;
        }
    }

    private static void throwUnchecked(InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(cause);
    }
}
