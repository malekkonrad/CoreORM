package pl.edu.agh.dp.core.mapping;

import pl.edu.agh.dp.api.annotations.Column;
import pl.edu.agh.dp.api.annotations.Id;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.util.StringUtils;

import java.lang.reflect.Field;
import java.util.Objects;

public class MetadataBuilder {
    public static EntityMetadata buildEntityMetadata(Class<?> clazz) {
        EntityMetadata meta = new EntityMetadata();
        meta.setEntityClass(clazz);

        // TODO: process @Entity, @Table
        // TODO: detect inheritance
        // TODO: detect associations

        // Basic mapping TODO add more complexity
        // @malekkonrad what do you mean by more complexity
        String name = clazz.getSimpleName();
        if (name.isBlank()) {
            throw new IntegrityException("Unable to identify the table name of: " + clazz.getName());
        }
        String tableName = StringUtils.convertCamelCaseToSnake(name) + "s";
        meta.setTableName(tableName);

        boolean hasSeenId = false;

        for (Field f : clazz.getDeclaredFields()) {
            boolean isId = false;
            boolean autoIncrement = false;
            if (f.isAnnotationPresent(Id.class)) {
                Id id = f.getAnnotation(Id.class);
                isId = true;
                autoIncrement = id.autoIncrement();
                hasSeenId = true;
            }
            String columnName = StringUtils.convertCamelCaseToSnake(f.getName());
            int length = 0;
            int scale = 0;
            int precision = 0;
            boolean isUnique = false;
            boolean isNullable = false;
            boolean isIndex = false;
            Object defaultValue = null;
            if (f.isAnnotationPresent(Column.class)) {
                Column column = f.getAnnotation(Column.class);
                if (!Objects.equals(column.columnName(), "")) columnName = column.columnName();
                isUnique = column.unique();
                isNullable = column.nullable();
                isIndex = column.index();
                if (Objects.equals(column.defaultValue(), "__UNSET__")) {
                    // try to cast default value to the corresponding type
                    defaultValue = f.getType().cast(column.defaultValue());
                }
            }
            PropertyMetadata pm =
                    new PropertyMetadata(
                            f.getName(),
                            columnName,
                            f.getType(),
                            getSqlType(f.getType(), length, scale, precision, autoIncrement),
                            isId,
                            autoIncrement,
                            isUnique,
                            isNullable,
                            isIndex,
                            defaultValue
                            );
            meta.addProperty(pm);
        }

        if (!hasSeenId) {
            throw new IntegrityException("Every table must have an Id, unable to determine id in: " + clazz.getName());
        }

        return meta;
    }

    public static <T> String getSqlType(
            Class<T> type,
            int length,
            int scale,
            int precision,
            boolean autoIncrement
    ) {
        // String types
        if (type == String.class) {
            if (length > 0) {
                return "VARCHAR(" + length + ")";
            }
            return "TEXT";
        }

        // Integer types
        if (type == int.class || type == Integer.class) {
            return autoIncrement ? "SERIAL" : "INTEGER";
        }

        if (type == long.class || type == Long.class) {
            return autoIncrement ? "BIGSERIAL" : "BIGINT";
        }

        if (type == short.class || type == Short.class) {
            return autoIncrement ? "SMALLSERIAL" : "SMALLINT";
        }
        // other auto increments are not supported
        if (autoIncrement) {
            throw new IntegrityException("Auto increment is supported only for int, long, and short. Not for: " + type);
        }

        // Floating point types
        if (type == float.class || type == Float.class) {
            return "REAL";
        }

        if (type == double.class || type == Double.class) {
            return "DOUBLE PRECISION";
        }

        // Decimal / precise numeric
        if (type == java.math.BigDecimal.class) {
            if (precision > 0) {
                if (scale > 0) {
                    return "NUMERIC(" + precision + ", " + scale + ")";
                }
                return "NUMERIC(" + precision + ")";
            }
            return "NUMERIC";
        }

        // Boolean
        if (type == boolean.class || type == Boolean.class) {
            return "BOOLEAN";
        }

        // Date & time
        if (type == java.time.LocalDate.class) {
            return "DATE";
        }

        if (type == java.time.LocalTime.class) {
            return "TIME";
        }

        if (type == java.time.LocalDateTime.class ||
                type == java.time.OffsetDateTime.class ||
                type == java.util.Date.class) {
            return "TIMESTAMP";
        }

        // UUID
        if (type == java.util.UUID.class) {
            return "UUID";
        }

        // Binary data
        if (type == byte[].class) {
            return "BYTEA";
        }

        throw new IntegrityException("Unsupported type: " + type);
    }
}
