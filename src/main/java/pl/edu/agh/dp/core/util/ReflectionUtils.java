package pl.edu.agh.dp.core.util;

import java.lang.reflect.Field;

public final class ReflectionUtils {

    private ReflectionUtils() {
    }

    public static Object getFieldValue(Object target, String fieldName) {
        try {
            Field f = findField(target.getClass(), fieldName);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Cannot read field " + fieldName +
                    " from " + target.getClass(), e);
        }
    }

    public static void setFieldValue(Object target, String fieldName, Object value) {
        try {
            Field f = findField(target.getClass(), fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Cannot write field " + fieldName +
                    " in " + target.getClass(), e);
        }
    }

    /**
     * Szuka pola w klasie oraz jej nadklasach.
     */
    public static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                current = current.getSuperclass(); // szukamy wyżej w hierarchii
            }
        }
        throw new NoSuchFieldException("Field " + fieldName + " not found in " + type);
    }
}

