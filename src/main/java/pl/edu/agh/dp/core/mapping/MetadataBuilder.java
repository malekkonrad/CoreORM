package pl.edu.agh.dp.core.mapping;

import pl.edu.agh.dp.api.annotations.*;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.util.StringUtils;

import java.beans.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;

public class MetadataBuilder {
    public static EntityMetadata buildEntityMetadata(Class<?> clazz) {
        EntityMetadata meta = new EntityMetadata();
        meta.setEntityClass(clazz);

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

        // 1. Sprawdzamy, czy znaleźliśmy ID w bieżącej klasie
        boolean hasLocalId = !idProperties.isEmpty();

        // 2. Jeśli nie ma lokalnie, sprawdzamy w górę hierarchii używając Refleksji
        if (!hasLocalId) {
            boolean hasParentId = checkRecursiveIdInParents(clazz);

            if (!hasParentId) {
                throw new IntegrityException("Entity " + clazz.getName() +
                        " has no @Id defined and does not inherit any @Id from superclasses.");
            }
        }



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

    private static boolean checkRecursiveIdInParents(Class<?> currentClass) {
        // Zaczynamy od rodzica (bo bieżącą klasę już sprawdziliśmy w 'idProperties')
        Class<?> superClass = currentClass.getSuperclass();

        while (superClass != null && superClass != Object.class) {
            // Sprawdzamy czy jakikolwiek pole w nadklasie ma adnotację @Id
            // Upewnij się, że używasz swojej adnotacji pl.edu.agh.dp.api.annotations.Id
            boolean hasId = Arrays.stream(superClass.getDeclaredFields())
                    .anyMatch(f -> f.isAnnotationPresent(pl.edu.agh.dp.api.annotations.Id.class));

            if (hasId) {
                return true; // Znaleziono ID u przodka
            }

            // Idziemy wyżej
            superClass = superClass.getSuperclass();
        }

        return false; // Doszliśmy do Object i nikt nie miał ID
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
                        "__UNSET__",
                        null
                );
        meta.addFkProperty(pm);
        if (isId) idProperties.add(pm);
    }

    private static List<PropertyMetadata> determineJoinColumns(EntityMetadata meta, Field f) {
        List<PropertyMetadata> joinColumns = new ArrayList<>();
        String[] columnNames;
        if (f.isAnnotationPresent(JoinColumn.class)) {
            JoinColumn join = f.getAnnotation(JoinColumn.class);
            columnNames = join.joinColumns();
            // FIXME clean this up
//        } else {
//            columnNames = new String[]{f.getName()};
//        }
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
                                "__UNSET__",
                                null
                        )
                );
            }
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
                null,
                meta.getTableName(),
                "",
                AssociationMetadata.CollectionType.NONE,
                joinColumns,
                new ArrayList<>(),
                null
        );
        meta.addAssociationMetadata(am);
    }

    private static void mapOneToManyColumns(EntityMetadata meta, Field f) {
        OneToMany annotation = f.getAnnotation(OneToMany.class);
        Type genericFieldType = f.getGenericType();
        Class<?> targetEntity;
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

        // check list or set
        Class<?> fieldType = f.getType();

        boolean isList = List.class.isAssignableFrom(fieldType);
        boolean isSet = Set.class.isAssignableFrom(fieldType);

        AssociationMetadata.CollectionType collectionType;
        if (!isList && !isSet) {
            throw new IntegrityException(
                    "ManyToMany field must be of type List or Set, but found: "
                            + fieldType.getSimpleName()
            );
        } else {
            // prefer list over set
            collectionType = isList ?
                    AssociationMetadata.CollectionType.LIST : AssociationMetadata.CollectionType.SET;
        }

        AssociationMetadata am = new AssociationMetadata(
                AssociationMetadata.Type.ONE_TO_MANY,
                targetEntity,
                f.getName(),
                annotation.mappedBy(),
                null,
                meta.getTableName(),
                "",
                collectionType,
                joinColumns,
                new ArrayList<>(),
                null
        );
        meta.addAssociationMetadata(am);
    }

    private static void mapManyToOneColumns(EntityMetadata meta, Field f) {
        ManyToOne annotation = f.getAnnotation(ManyToOne.class);
        Type genericFieldType = f.getGenericType();
        if(genericFieldType instanceof ParameterizedType){
            throw new IntegrityException(
                    "Invalid type: '" + f.getType().getSimpleName() + "' in many to one relationship.\n" +
                    "Source class: " + meta.getEntityClass().getName() + "\n" +
                    "Field: " + f.getName() + "\n" +
                    "Many to one expects only a table class not parameterized collection type.");
        }
        List<PropertyMetadata> joinColumns = determineJoinColumns(meta, f);

        AssociationMetadata am = new AssociationMetadata(
                AssociationMetadata.Type.MANY_TO_ONE,
                f.getType(),
                f.getName(),
                annotation.mappedBy(),
                null,
                meta.getTableName(),
                "",
                AssociationMetadata.CollectionType.NONE,
                joinColumns,
                new ArrayList<>(),
                null
        );
        meta.addAssociationMetadata(am);
    }

    private static void mapManyToManyColumns(EntityMetadata meta, Field f) {
        ManyToMany annotation = f.getAnnotation(ManyToMany.class);
        Type genericFieldType = f.getGenericType();
        Class<?> targetEntity;
        if (genericFieldType instanceof ParameterizedType aType) {
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

        // check list or set
        Class<?> fieldType = f.getType();

        boolean isList = List.class.isAssignableFrom(fieldType);
        boolean isSet = Set.class.isAssignableFrom(fieldType);

        AssociationMetadata.CollectionType collectionType;
        if (!isList && !isSet) {
            throw new IntegrityException(
                    "ManyToMany field must be of type List or Set, but found: "
                            + fieldType.getSimpleName()
            );
        } else {
            // prefer list over set
            collectionType = isList ?
                    AssociationMetadata.CollectionType.LIST : AssociationMetadata.CollectionType.SET;
        }

        AssociationMetadata am = new AssociationMetadata(
                AssociationMetadata.Type.MANY_TO_MANY,
                targetEntity,
                f.getName(),
                annotation.mappedBy(),
                null,
                meta.getTableName(),
                "",
                collectionType,
                joinColumns,
                new ArrayList<>(),
                null
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
        Object defaultValue = "__UNSET__";
        if (f.isAnnotationPresent(Column.class)) {
            Column column = f.getAnnotation(Column.class);
            if (!Objects.equals(column.columnName(), "")) columnName = column.columnName();
            isUnique = column.unique();
            isNullable = column.nullable();
            isIndex = column.index();
            if (!Objects.equals(column.defaultValue(), "__UNSET__")) {
                // try to cast default value to the corresponding type
                if (f.getType().equals(Integer.class)) {
                    defaultValue = Integer.parseInt(column.defaultValue());
                } else if (f.getType().equals(Long.class)) {
                    defaultValue = Long.parseLong(column.defaultValue());
                } else if (f.getType().equals(Short.class)) {
                    defaultValue = Short.parseShort(column.defaultValue());
                } else if (f.getType().equals(Float.class)) {
                    defaultValue = Float.parseFloat(column.defaultValue());
                } else if (f.getType().equals(Double.class)) {
                    defaultValue = Double.parseDouble(column.defaultValue());
                } else if (f.getType().equals(Boolean.class)) {
                    defaultValue = Boolean.parseBoolean(column.defaultValue());
                } else if (f.getType().equals(BigDecimal.class)) {
                    defaultValue = new BigDecimal(column.defaultValue());
                } else if (f.getType().equals(LocalDate.class)) {
                    defaultValue = LocalDate.parse(column.defaultValue());
                } else if (f.getType().equals(LocalTime.class)) {
                    defaultValue = LocalTime.parse(column.defaultValue()); // FIXME
                } else if (f.getType().equals(LocalDateTime.class)) {
                    defaultValue = LocalDateTime.parse(column.defaultValue());
                } else if (f.getType().equals(OffsetDateTime.class)) {
                    defaultValue = OffsetDateTime.parse(column.defaultValue());
                } else if (f.getType().equals(UUID.class)) {
                    defaultValue = UUID.fromString(column.defaultValue());
                } else {
                    // fallback
                    defaultValue = f.getType().cast(column.defaultValue());
                }
            }
        }
        PropertyMetadata pm =
                new PropertyMetadata(
                        f.getName(),
                        columnName,
                        f.getType(),
                        getSqlType(meta, f, f.getType(), length, scale, precision, autoIncrement),
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
            EntityMetadata meta,
            Field f,
            Class<T> type,
            int length,
            int scale,
            int precision,
            boolean autoIncrement
    ) {
        // unsupported primary types
        String errorText = "Unsupported type.\n" +
                               "Class: " + meta.getEntityClass() + "\n" +
                               "Field: " + f.getName() + "\n" +
                               "'%s' class is not supported please use the '%s' class instead.";
        final Map<Class<?>, String> forbiddenType = new HashMap<>() {{
            put(int.class, "Integer");
            put(long.class, "Long");
            put(short.class, "Short");
            put(float.class, "Float");
            put(double.class, "Double");
            put(boolean.class, "Boolean");
        }};
        if (forbiddenType.containsKey(type)) {
            throw new IntegrityException(String.format(errorText, type.getSimpleName(), forbiddenType.get(type)));
        }
        // Integer types
        if (type == Integer.class) {
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

        // String types
        if (type == String.class) {
            if (length > 0) {
                return "VARCHAR(" + length + ")";
            }
            return "TEXT";
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

        if (type == java.time.LocalDateTime.class) {
            return "DATETIME";
        }

        if (type == java.time.OffsetDateTime.class) {
            return "TIMESTAMP WITH TIME ZONE";
        }

        // UUID
        if (type == java.util.UUID.class) {
            return "UUID";
        }

        // FIXME detect Collection and suggest relationship
        throw new IntegrityException("Unsupported type: " + type);
    }
}
