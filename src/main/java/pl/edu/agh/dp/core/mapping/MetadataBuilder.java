package pl.edu.agh.dp.core.mapping;

import pl.edu.agh.dp.api.annotations.*;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.util.StringUtils;

import java.beans.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public class MetadataBuilder {
    public static EntityMetadata buildEntityMetadata(Class<?> clazz) {
        EntityMetadata meta = new EntityMetadata();
        meta.setEntityClass(clazz);

        // TODO: detect associations

        String name = clazz.getSimpleName();
        if (name.isBlank()) {
            throw new IntegrityException("Unable to identify the table name of: " + clazz.getName());
        }
        String tableName = StringUtils.convertCamelCaseToSnake(name) + "s";
        if (clazz.isAnnotationPresent(Table.class)) {
            Table annotation = clazz.getAnnotation(Table.class);
            tableName = annotation.name();
        }
        meta.setTableName(tableName);

        List<PropertyMetadata> idProperties = new ArrayList<>();

        for (Field f : clazz.getDeclaredFields()) {
            // foreign keys and relationships
            boolean isForeignColumn = true;
            if (f.isAnnotationPresent(OneToOne.class)) {
                mapOneToOneColumns(meta, f);
            } else if (f.isAnnotationPresent(OneToMany.class)) {
                mapOneToManyColumns(meta, f);
            } else if (f.isAnnotationPresent(ManyToOne.class)) {
                mapManyToOneColumns(meta, f);
            } else if (f.isAnnotationPresent(ManyToMany.class)) {
                mapManyToManyColumns(meta, f);
            } else {
                // default column properties
                mapDefaultColumns(meta, f, idProperties);
                isForeignColumn = false;
            }
            // add foreign column, by default add everything, fix later
            if (isForeignColumn) {
                mapForeignColumn(meta, f, idProperties);
            }
        }
        // FIXME
        // Error checking for id
//        if (idProperties.isEmpty()) {
//            throw new IntegrityException("Every table must have an Id." +
//                    "Unable to determine id in entity: " + clazz.getName());
//        }
        // Error checking for composite keys
        if (idProperties.size() > 1) {
            List<PropertyMetadata> idIncremented = new ArrayList<>();
            for (PropertyMetadata pm : idProperties) {
                if (pm.isAutoIncrement()) {
                    idIncremented.add(pm);
                }
            }
            if (!idIncremented.isEmpty()) {
                List<String> fields = new ArrayList<>();
                for (PropertyMetadata pm : idIncremented) {
                    fields.add(pm.getName());
                }
                List<String> incrementedFields = new ArrayList<>();
                for (PropertyMetadata pm : idIncremented) {
                    incrementedFields.add(pm.getName());
                }
                throw new IntegrityException("Auto increment is not supported for composite keys.\n" +
                        "Composite keys: (" + String.join(", ", fields) + ")\n" +
                        "Auto incremented: (" + String.join(", ", incrementedFields) + ")\n" +
                        "Entity: " + clazz.getName());
            }
        }

        return meta;
    }

    private static void mapForeignColumn(EntityMetadata meta, Field f, List<PropertyMetadata> idProperties) {
        boolean isId = false;
        if (f.isAnnotationPresent(Id.class)) {
            Id id = f.getAnnotation(Id.class);
            isId = true;
            if (id.autoIncrement()) {
                throw new IntegrityException("Relationship's id cannot be set to autoincrement: " + meta.getEntityClass().getName() + "." + f.getName());
            }
        }

        if (f.isAnnotationPresent(Column.class)) {
            throw new IntegrityException("Relationship cannot be annotated with column, annotate it with 'JoinColumn', at: " + meta.getEntityClass().getName() + "." + f.getName());
        }

        boolean isNullable = false;
        if (f.isAnnotationPresent(JoinColumn.class)) {
            JoinColumn join = f.getAnnotation(JoinColumn.class);
            isNullable = join.nullable();
        }
        String columnName = StringUtils.convertCamelCaseToSnake(f.getName());
        // foreign key, removed later if unnecessary
        PropertyMetadata pm =
                new PropertyMetadata(
                        f.getName(),
                        columnName,
                        f.getType(),
                        "INTEGER",
                        isId,
                        false,
                        false,
                        isNullable,
                        false,
                        false,
                        null
                );
        meta.addProperty(pm);
        if (isId) idProperties.add(pm);
    }

    private static List<PropertyMetadata> determineJoinColumns(EntityMetadata meta, Field f) {
        List<PropertyMetadata> joinColumns = new ArrayList<>();
        String[] columnNames;
        if (f.isAnnotationPresent(JoinColumn.class)) {
            JoinColumn join = f.getAnnotation(JoinColumn.class);
            columnNames = join.joinColumns();
        } else {
            columnNames = new String[]{f.getName()};
        }
        for (String name : columnNames) {
            // just a placeholder for later
            joinColumns.add(
                    new PropertyMetadata(
                            name.isBlank() ? f.getName() : name,
                            null,
                            null,
                            null,
                            false,
                            false,
                            false,
                            false,
                            false,
                            null,
                            null
                    )
            );
        }
        return joinColumns;
    }

    private static void mapOneToOneColumns(EntityMetadata meta, Field f) {
        OneToOne annotation = f.getAnnotation(OneToOne.class);
        Type genericFieldType = f.getGenericType();
        if(genericFieldType instanceof ParameterizedType){
            throw new IntegrityException("Invalid type: '" + f.getType().getSimpleName() + "' in one to one relationship.\n" +
                    "One to one expects only a table class not parameterized collection type.");
        }
        List<PropertyMetadata> joinColumns = determineJoinColumns(meta, f);

        AssociationMetadata am = new AssociationMetadata(
                AssociationMetadata.Type.ONE_TO_ONE,
                f.getType(),
                f.getName(),
                annotation.mappedBy(),
                "",
                joinColumns,
                new ArrayList<>()
        );
        meta.addAssociationMetadata(am);
    }

    private static void mapOneToManyColumns(EntityMetadata meta, Field f) {
        OneToMany annotation = f.getAnnotation(OneToMany.class);
        Type genericFieldType = f.getGenericType();
        Class<?> targetEntity;
        // FIXME check for Collection
        if(genericFieldType instanceof ParameterizedType aType) {
            Type[] fieldArgTypes = aType.getActualTypeArguments();
            if (fieldArgTypes.length != 1) {
                throw new IntegrityException("Expected only one parameterized type, but got: " + Arrays.toString(fieldArgTypes));
            }
            targetEntity = (Class<?>) fieldArgTypes[0];
        } else {
            throw new IntegrityException("Invalid type: '" + f.getType().getSimpleName() + "' in one to many relationship.\n" +
                    "One to many expects parameterized collection type.");
        }
        List<PropertyMetadata> joinColumns = determineJoinColumns(meta, f);

        AssociationMetadata am = new AssociationMetadata(
                AssociationMetadata.Type.ONE_TO_MANY,
                targetEntity,
                f.getName(),
                annotation.mappedBy(),
                "",
                joinColumns,
                new ArrayList<>()
        );
        meta.addAssociationMetadata(am);
    }

    private static void mapManyToOneColumns(EntityMetadata meta, Field f) {
        ManyToOne annotation = f.getAnnotation(ManyToOne.class);
        Type genericFieldType = f.getGenericType();
        if(genericFieldType instanceof ParameterizedType){
            throw new IntegrityException("Invalid type: '" + f.getType().getSimpleName() + "' in many to one relationship.\n" +
                    "Many to one expects only a table class not parameterized collection type.");
        }
        List<PropertyMetadata> joinColumns = determineJoinColumns(meta, f);

        AssociationMetadata am = new AssociationMetadata(
                AssociationMetadata.Type.MANY_TO_MANY,
                f.getType(),
                f.getName(),
                annotation.mappedBy(),
                "",
                joinColumns,
                new ArrayList<>()
        );
        meta.addAssociationMetadata(am);
    }

    private static void mapManyToManyColumns(EntityMetadata meta, Field f) {
        ManyToMany annotation = f.getAnnotation(ManyToMany.class);
        Type genericFieldType = f.getGenericType();
        Class<?> targetEntity;
        // FIXME check for Collection
        if(genericFieldType instanceof ParameterizedType aType) {
            Type[] fieldArgTypes = aType.getActualTypeArguments();
            if (fieldArgTypes.length != 1) {
                throw new IntegrityException("Expected only one parameterized type, but got: " + Arrays.toString(fieldArgTypes));
            }
            targetEntity = (Class<?>) fieldArgTypes[0];
        } else {
            throw new IntegrityException("Invalid type: '" + f.getType().getSimpleName() + "' in many to Many relationship.\n" +
                    "Many to many expects parameterized collection type.");
        }
        List<PropertyMetadata> joinColumns = determineJoinColumns(meta, f);

        AssociationMetadata am = new AssociationMetadata(
                AssociationMetadata.Type.MANY_TO_MANY,
                targetEntity,
                f.getName(),
                annotation.mappedBy(),
                "",
                joinColumns,
                new ArrayList<>()
        );
        meta.addAssociationMetadata(am);
    }

    private static void mapDefaultColumns(EntityMetadata meta, Field f, List<PropertyMetadata> idProperties) {
        boolean isId = false;
        boolean autoIncrement = false;
        if (f.isAnnotationPresent(Id.class)) {
            Id id = f.getAnnotation(Id.class);
            isId = true;
            autoIncrement = id.autoIncrement();
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
                        defaultValue,
                        null
                );
        meta.addProperty(pm);
        if (isId) idProperties.add(pm);
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
        // FIXME detect Collection and suggest relationship
        throw new IntegrityException("Unsupported type: " + type);
    }
}
