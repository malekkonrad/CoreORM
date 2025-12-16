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

    private static void mapForeignColumn(EntityMetadata meta, Field f) {

    }


//    public static Map<Class<?>, EntityMetadata> buildInheritanceMetadataMap(Map<Class<?>, EntityMetadata> entities) {
//        Map<Class<?>, List<Class<?>>> rootToSubclasses = new HashMap<>();
//
//        for (Class<?> clazz : entities.keySet()) {
//            if (clazz.isAnnotationPresent(Inheritance.class)) {
//                rootToSubclasses.put(clazz, new ArrayList<>());
//            }
//        }
//
//        // Krok 2: przejdź wszystkie encje i przypisz je do rootów
//        for (Class<?> clazz : entities.keySet()) {
//            Class<?> current = clazz.getSuperclass();
//            while (current != null && current != Object.class) {
//                if (rootToSubclasses.containsKey(current)) {
//                    rootToSubclasses.get(current).add(clazz);
//                    break;
//                }
//                current = current.getSuperclass();
//            }
//        }
//
//        // Krok 3: dla każdego roota zbuduj InheritanceMetadata
//        for (Map.Entry<Class<?>, List<Class<?>>> entry : rootToSubclasses.entrySet()) {
//            Class<?> rootClass = entry.getKey();
//            List<Class<?>> subclasses = entry.getValue();
//
//            EntityMetadata rootBuilder = entities.get(rootClass);
//            InheritanceType type = getStrategy(rootClass);
//
//            InheritanceMetadata inheritanceMetadata = buildInheritanceMetadata(rootClass, subclasses, type);
//
////            System.out.println(inheritanceMetadata);
//
//            rootBuilder.setInheritanceMetadata(inheritanceMetadata);
//
//            // ustaw rootMetadata dla podklas
//            for (Class<?> sub : subclasses) {
//                entities.get(sub).setRootMetadata(rootClass);
////                System.out.println(sub.toString());
//            }
//
//        }
//
//        return entities;
//
//    }


//    private static InheritanceMetadata buildInheritanceMetadata(Class<?> rootClass,
//                                                                List<Class<?>> subclasses,
//                                                                InheritanceType type) {
//
//        String discriminatorColumnName = null;
//        String discriminatorColumnType = null;
//        Map<Class<?>, String> classToDisc = new HashMap<>();
//        Map<String, Class<?>> discToClass = new HashMap<>();
//
//        if (type == InheritanceType.SINGLE_TABLE) {
//            DiscriminatorValue discColAnn =
//                    rootClass.getAnnotation(DiscriminatorValue.class);
//
//            discriminatorColumnName = discColAnn != null
//                    ? discColAnn.value()
//                    : "dtype";
//
//            // uproszczenie – zawsze varchar(31)
//            discriminatorColumnType = "varchar(31)";
//
//            // root też może mieć wartość discriminatora
//            addDiscriminatorMapping(rootClass, classToDisc, discToClass);
//
//            for (Class<?> sub : subclasses) {
//                addDiscriminatorMapping(sub, classToDisc, discToClass);
//            }
//        }
//
//        // Subclass EntityMetadata przypniemy później, gdy buildery zostaną zmaterializowane
//        return new InheritanceMetadata(type,
//                discriminatorColumnName,
//                discriminatorColumnType,
//                classToDisc,
//                discToClass,
//                subclasses);
////                /*subclasses = tymczasowo null, wypełnimy w Registry*/ List.of());
//    }

//    private static void addDiscriminatorMapping(Class<?> clazz,
//                                                Map<Class<?>, String> classToDisc,
//                                                Map<String, Class<?>> discToClass) {
//        DiscriminatorValue dv = clazz.getAnnotation(DiscriminatorValue.class);
//        String value = (dv != null) ? dv.value() : clazz.getSimpleName();
//        classToDisc.put(clazz, value);
//        discToClass.put(value, clazz);
//    }



//    private static InheritanceType getStrategy(Class<?> clazz) {
//        Class<?> current = clazz;
//        while (current != Object.class) {
//            if (current.isAnnotationPresent(Inheritance.class)) {
//                return current.getAnnotation(Inheritance.class).strategy();
//            }
//            current = current.getSuperclass();
//        }
//        // Default SINGLE_TABLE
//        if (clazz.getSuperclass() != Object.class && clazz.getSuperclass().isAnnotationPresent(Entity.class)) {
//            return InheritanceType.SINGLE_TABLE;
//        }
//        return InheritanceType.SINGLE_TABLE; // FIXME: zastanawiam się co w przypadku braku dziedziczenia - NoInheritance???
//    }

//    public static List<Field> getAllFieldsUpToEntity(Class<?> startClass) {
//        List<Field> fields = new ArrayList<>();
//        Class<?> current = startClass;
//
//        while (current != Object.class && current.isAnnotationPresent(Entity.class)) {
//            fields.addAll(Arrays.asList(current.getDeclaredFields()));
//            current = current.getSuperclass();
//        }
//        return fields;
//    }
//
//    public static Class<?> getRootEntity(Class<?> clazz) {
//        Class<?> current = clazz;
//        while (current.getSuperclass() != Object.class && current.getSuperclass().isAnnotationPresent(Entity.class)) {
//            current = current.getSuperclass();
//        }
//        return current;
//    }


    private static void mapOneToOneColumns(EntityMetadata meta, Field f) {
        OneToOne annotation = f.getAnnotation(OneToOne.class);
        Type genericFieldType = f.getGenericType();
        if(genericFieldType instanceof ParameterizedType){
            throw new IntegrityException("Invalid type: '" + f.getType().getSimpleName() + "' in one to one relationship.\n" +
                    "One to one expects only a table class not parameterized type.");
        }
        AssociationMetadata am = new AssociationMetadata(
                AssociationMetadata.Type.ONE_TO_ONE,
                f.getType(),
                annotation.mappedBy(),
                "",
                new ArrayList<>(),
                new ArrayList<>()
        );
        meta.addAssociationMetadata(am);
    }

    private static void mapOneToManyColumns(EntityMetadata meta, Field f) {
        OneToMany annotation = f.getAnnotation(OneToMany.class);
        Type genericFieldType = f.getGenericType();
        if(!(genericFieldType instanceof ParameterizedType)){
            throw new IntegrityException("Invalid type: '" + f.getType().getSimpleName() + "' in one to many relationship.\n" +
                    "One to many expects parameterized collection type.");
        }
        AssociationMetadata am = new AssociationMetadata(
                AssociationMetadata.Type.ONE_TO_MANY,
                f.getType(),
                annotation.mappedBy(),
                "",
                new ArrayList<>(),
                new ArrayList<>()
        );
        meta.addAssociationMetadata(am);
    }

    private static void mapManyToOneColumns(EntityMetadata meta, Field f) {
        ManyToOne annotation = f.getAnnotation(ManyToOne.class);
        Type genericFieldType = f.getGenericType();
        if(genericFieldType instanceof ParameterizedType){
            throw new IntegrityException("Invalid type: '" + f.getType().getSimpleName() + "' in many to one relationship.\n" +
                    "Many to one expects only a table class not parameterized type.");
        }
        AssociationMetadata am = new AssociationMetadata(
                AssociationMetadata.Type.MANY_TO_MANY,
                f.getType(),
                annotation.mappedBy(),
                "",
                new ArrayList<>(),
                new ArrayList<>()
        );
        meta.addAssociationMetadata(am);
    }

    private static void mapManyToManyColumns(EntityMetadata meta, Field f) {
        ManyToMany annotation = f.getAnnotation(ManyToMany.class);
        Type genericFieldType = f.getGenericType();
        if(genericFieldType instanceof ParameterizedType){
            throw new IntegrityException("Invalid type: '" + f.getType().getSimpleName() + "' in many to Many relationship.\n" +
                    "Many to many expects only a table class not parameterized type.");
        }
        AssociationMetadata am = new AssociationMetadata(
                AssociationMetadata.Type.MANY_TO_MANY,
                f.getType(),
                annotation.mappedBy(),
                "",
                new ArrayList<>(),
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
                        defaultValue
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

        throw new IntegrityException("Unsupported type: " + type);
    }
}
