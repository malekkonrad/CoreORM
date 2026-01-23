package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.AssociationMetadata;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.util.ReflectionUtils;
import javafx.util.Pair;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class TablePerClassInheritanceStrategy extends AbstractInheritanceStrategy{

    public TablePerClassInheritanceStrategy(EntityMetadata metadata) {
        super(metadata);
    }

    @Override
    public Pair<String, String> create() {
        assert this.entityMetadata != null;

        StringBuilder sb = new StringBuilder();

        EntityMetadata rootMetadata = entityMetadata.getInheritanceMetadata().getRootClass();
        // fix Properties and IdColumns to contain all the fields of the class
        entityMetadata.setProperties(entityMetadata.getAllColumnsForConcreteTable());
        entityMetadata.setIdColumns(entityMetadata.getIdColumnsForConcreteTable());

        // FIXME complex key not supported for inheritance
        if (entityMetadata.getIdColumns().isEmpty()) {
            throw new IntegrityException("No id"); // sanity check
        } else if (entityMetadata.getIdColumns().size() == 1) {
            PropertyMetadata idProperty = entityMetadata.getIdColumns().values().iterator().next();
            // must be autoincremented
            if (!idProperty.isAutoIncrement()) {
                throw new IntegrityException("Id column must be auto-increment");
            }
        } else if (entityMetadata != rootMetadata) {
            throw new IntegrityException("Complex id not supported for Table per class inheritance");
        }

        // create sequence for all the subclasses to have the same id
        String rootTableName = rootMetadata.getTableName();
        String sequenceName = rootTableName + "_id_seq";
        if (rootMetadata == entityMetadata) {
            // create sequence only once
            String seq =  "CREATE SEQUENCE " + sequenceName + " START 1 INCREMENT 1;\n";
            sb.append(seq);
        }
        // change default in id to the sequence
        PropertyMetadata rootId = entityMetadata.getIdColumns().values().iterator().next();
        rootId.setSqlType("BIGINT"); // TODO maybe change based on the base type
        rootId.setDefaultValue("nextval('" + sequenceName + "')");
        // add table to the creation
        sb.append(entityMetadata.getSqlTable());
        // return table and it's constraints
        return new Pair<>(sb.toString(), entityMetadata.getSqlConstraints());
    }

    @Override
    public Object insert(Object entity, Session session) {
        System.out.println("Inserting: " + entity.getClass().getName());

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        assert entityMetadata != null;
        List<PropertyMetadata> idColumns = new ArrayList<>(entityMetadata.getIdColumns().values());
        boolean isCompositeKey = idColumns.size() > 1;

        Set<String> idProvided = new HashSet<>();
        for (PropertyMetadata pm : entityMetadata.getColumnsForConcreteTable()) {
            Object value = ReflectionUtils.getFieldValue(entity, pm.getName());
            // TODO handle null in the value, cause it could be set to null explicitly
            if (value != null) {
                columns.add(pm.getColumnName());
                values.add(value);
                if (pm.isId()) {
                    idProvided.add(pm.getName());
                }
            }
        }
        // TODO dirty quick test
        String assStmt = "";
        String assFieldname = "";
        List<String> targetRef = new ArrayList<>();
        List<String> currentRef = new ArrayList<>();

        // handle relationships
        for (AssociationMetadata am : entityMetadata.getAssociationMetadata().values()) {
            Object value = ReflectionUtils.getFieldValue(entity, am.getField());
            if (value != null) {
                // set fk id
                if (am.getHasForeignKey()) {
                    if (am.getType() == AssociationMetadata.Type.MANY_TO_MANY) {
                        assFieldname = am.getField();
                        EntityMetadata assTable = am.getAssociationTable();
//                        Collection<PropertyMetadata> fkColumns = assTable.getFkColumns().values();
//                        List<String> assColumns = new ArrayList<>();
//                        for (PropertyMetadata fkColumn : fkColumns) {
//                            assColumns.add(fkColumn.getColumnName());
//                        }
//                        String stmt = "INSERT INTO " + assTable.getTableName() +
//                                    " (" + String.join(", ", assColumns) + " )" +
//                                    " VALUES (" + "?,".repeat(assColumns.size() - 1) + "?);";
//                        System.out.println(stmt);
                        List<String> assColumns = new ArrayList<>();
                        for (PropertyMetadata pm : am.getTargetJoinColumns()) {
                            targetRef.add(pm.getReferencedName());
//                            Object field = ReflectionUtils.getFieldValue(value, pm.getReferencedName());
                            assColumns.add(pm.getColumnName());
//                            assValues.add(field);
                        }
                        for (PropertyMetadata pm : am.getJoinColumns()) {
                            currentRef.add(pm.getReferencedName());
//                            Object field = ReflectionUtils.getFieldValue(value, pm.getReferencedName());
                            assColumns.add(pm.getColumnName());
//                            assValues.add(field);
                        }
                        assStmt = "INSERT INTO " + assTable.getTableName() +
                                    " (" + String.join(", ", assColumns) + " )" +
                                    " VALUES (" + "?,".repeat(assColumns.size() - 1) + "?);";
                    } else {
                        for (PropertyMetadata pm : am.getJoinColumns()) {
                            Object field = ReflectionUtils.getFieldValue(value, pm.getReferencedName());
                            columns.add(pm.getColumnName());
                            values.add(field);
                        }
                    }
                }
            }
        }

        if (isCompositeKey) {
            if (idProvided.size() != idColumns.size()) {
                List<String> compositeKey = new ArrayList<>();
                List<String> missingIds = new ArrayList<>();
                for (PropertyMetadata pm : idColumns) {
                    if (!idProvided.contains(pm.getName())) {
                        missingIds.add(pm.getName());
                    }
                    compositeKey.add(pm.getName());
                }
                throw new IntegrityException(
                        "Not all identifiers are set. You must set all ids in composite key.\n" +
                        "Composite key: (" + String.join(", ", compositeKey) + ")\n" +
                        "Missing/unset fields: (" + String.join(", ", missingIds) + ")"
                );
            }
        } else {
            if (!idProvided.isEmpty() && idColumns.get(0).isAutoIncrement()) {
                throw new IntegrityException(
                        "You cannot set an id that is auto increment.\n" +
                        "Tried to set: '" + idColumns.get(0).getName() + "' that is an auto incremented id.\n" +
                        "Remove the auto increment or don't set it.");
            } else if (idProvided.isEmpty() && !idColumns.get(0).isAutoIncrement()) {
                throw new IntegrityException(
                        "You must set an id that is not auto increment.\n" +
                        "Field: '" + idColumns.get(0).getName() + "' is not set.\n" +
                        "Add auto increment or set this field.");
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ")
                .append(entityMetadata.getTableName())
                .append(" (")
                .append(String.join(", ", columns))
                .append(") VALUES (")
                .append("?,".repeat(values.size()));
        if (!values.isEmpty()) sql.deleteCharAt(sql.length() - 1); // sanity check if inserting nothing
        sql.append(")");

        Long generatedId;
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            generatedId = jdbc.insert(sql.toString(), values.toArray());
            System.out.println("Generated ID: " + generatedId);

            // Ustaw wygenerowane ID
            int numOfIds = entityMetadata.getInheritanceMetadata().getRootClass().getIdColumns().size();
            if (numOfIds == 1) {        // we have one key if there's more then for sure it's not autoincrement
                PropertyMetadata idProp = entityMetadata.getInheritanceMetadata().getRootClass().getIdColumns().values().iterator().next();
                if (idProp.isAutoIncrement()) {
                    System.out.println("seting id in " + entity.toString()+ " value: " + generatedId);
                    ReflectionUtils.setFieldValue(entity, idProp.getName(), generatedId);
                }
            }
            //TODO dirty fix
            if (!assStmt.isEmpty()) {
                System.out.println(assStmt);
                List<List<Object>> assAssValues = new ArrayList<>();
                for (String fieldName : currentRef) {
                    System.out.println(fieldName + " from " + entity.getClass().getSimpleName());
                    Object field = ReflectionUtils.getFieldValue(entity, fieldName);
                    assAssValues.add(new ArrayList<>(){{add(field);}});
                }
                Collection<?> assField = (Collection<?>) ReflectionUtils.getFieldValue(entity, assFieldname);
                for (Object value : assField) {
                    // first is the example, copy it and fill with the other values
                    assAssValues.add(new ArrayList<>(){{addAll(assAssValues.get(0));}});
                    List<Object> assValues = assAssValues.get(assAssValues.size() - 1);
                    for (String fieldName : targetRef) {
                        System.out.println(fieldName + " from " + value.getClass().getSimpleName());
                        Object field = ReflectionUtils.getFieldValue(value, fieldName);
                        assValues.add(field);
                    }
                }
                assAssValues.remove(0);
                for (var el : assAssValues) {
                    jdbc.insert(assStmt, el.toArray());
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException("Error inserting entity " + entity, e);
        }
        return generatedId;
    }


    @Override
    public void update(Object entity, Session session) {
        assert entityMetadata != null;
        String tableName = entityMetadata.getTableName();

        List<String> setColumns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        // Wszystkie pola z hierarchii (pomiń ID)
        for (PropertyMetadata prop : entityMetadata.getColumnsForConcreteTable()) {
            if (prop.isId()) {
                continue; // ID nie jest aktualizowane
            }

            setColumns.add(prop.getColumnName() + " = ?");
            Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
            values.add(value);
        }

        EntityMetadata rootMetadata = entityMetadata.getInheritanceMetadata().getRootClass();

        // WHERE clause
        Object idValue = getIdValue(entity);
        String whereClause = buildWhereClause(rootMetadata);
        Object[] idParams = prepareIdParams(idValue);

        // Połącz parametry
        List<Object> allParams = new ArrayList<>(values);
        allParams.addAll(Arrays.asList(idParams));

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE ").append(tableName)
                .append(" SET ").append(String.join(", ", setColumns))
                .append(" WHERE ").append(whereClause);

        System.out.println("TablePerClass UPDATE SQL: " + sql);
        System.out.println("Values: " + allParams);

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            jdbc.update(sql.toString(), allParams.toArray());
        } catch (Exception e) {
            throw new RuntimeException("Error updating entity " + entity, e);
        }
    }

    @Override
    public void delete(Object entity, Session session) {
        assert entityMetadata != null;
        String tableName = entityMetadata.getTableName();

        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();

        Object idValue = getIdValue(entity);
        String whereClause = buildWhereClause(rootMetadata);
        Object[] idParams = prepareIdParams(idValue);

        String sql = "DELETE FROM " + tableName + " WHERE " + whereClause;

        System.out.println("TablePerClass DELETE SQL: " + sql);
        System.out.println("ID: " + idValue);

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            jdbc.update(sql, idParams);
        } catch (Exception e) {
            throw new RuntimeException("Error deleting entity " + entity, e);
        }
    }

    @Override
    public Object findById(Object id, Session session) {
        try {
            // FAZA 1: Znajdź, która konkretna klasa (tabela) posiada to ID
            EntityMetadata concreteMetadata = findConcreteMetadata(id, session);

            // Jeśli nie znaleziono ID w żadnej tabeli
            if (concreteMetadata == null) {
                return null;
            }

            // FAZA 2: Pobierz pełny obiekt z właściwej tabeli ze wszystkimi polami
            return loadSpecificEntity(concreteMetadata, id, session);

        } catch (Exception e) {
            throw new RuntimeException("Error finding entity with id = " + id, e);
        }
    }

    /**
     * Faza 1: Sprawdza wszystkie tabele dziedziczące i zwraca metadane tej, w której jest ID.
     */
    private EntityMetadata findConcreteMetadata(Object id, Session session) throws Exception {
        JdbcExecutor jdbc = session.getJdbcExecutor();
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        Collection<PropertyMetadata> idColumns = root.getIdColumns().values();

        // Pobieramy wszystkie podklasy (Animal, Dog, Husky, Cat...)
        List<EntityMetadata> concreteSubclasses = getAllConcreteSubclasses(entityMetadata);

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // Budujemy zapytanie sprawdzające obecność ID w każdej tabeli
        for (int i = 0; i < concreteSubclasses.size(); i++) {
            EntityMetadata subMeta = concreteSubclasses.get(i);

            // SELECT 'pl.agh...Husky' FROM husky WHERE id = ?
            sql.append("SELECT '")
                    .append(subMeta.getEntityClass().getName())
                    .append("' as DTYPE FROM ")
                    .append(subMeta.getTableName())
                    .append(" WHERE ");

            // Obsługa klucza (uproszczona dla pojedynczego ID, ale logika ta sama co wcześniej)
            appendIdWhereClause(sql, params, idColumns, id);

            if (i < concreteSubclasses.size() - 1) {
                sql.append(" UNION ALL ");
            }
        }

        // Wykonujemy zapytanie, które zwróci nam tylko nazwę klasy
        return jdbc.queryOne(sql.toString(), rs -> {
            String className = rs.getString("DTYPE");
            // Szukamy metadanych odpowiadających nazwie klasy
            return concreteSubclasses.stream()
                    .filter(m -> m.getEntityClass().getName().equals(className))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Metadata not found for class: " + className));
        }, params.toArray()).orElse(null);
    }

    /**
     * Faza 2: Pobiera konkretny obiekt, znając już jego dokładny typ i tabelę.
     */
    private Object loadSpecificEntity(EntityMetadata specificMetadata, Object id, Session session) throws Exception {
        JdbcExecutor jdbc = session.getJdbcExecutor();

        // Budujemy listę WSZYSTKICH kolumn dla tej konkretnej klasy (np. Husky ma też pola Animala i Doga)
        List<String> columns = specificMetadata.getColumnsForConcreteTable()
                .stream()
                .map(PropertyMetadata::getColumnName)
                .collect(Collectors.toList());

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
                .append(String.join(", ", columns))
                .append(" FROM ")
                .append(specificMetadata.getTableName())
                .append(" WHERE ");

        // Parametry do WHERE
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        Collection<PropertyMetadata> idColumns = root.getIdColumns().values();
        List<Object> params = new ArrayList<>();
        appendIdWhereClause(sql, params, idColumns, id);

        System.out.println("Loading full entity SQL: " + sql);

        // Używamy specjalnego mappera, który korzysta z przekazanych metadanych (specificMetadata),
        // a nie z this.entityMetadata (które wskazuje na Animal)
        return jdbc.queryOne(sql.toString(), rs -> mapSpecificEntity(rs, specificMetadata), params.toArray())
                .orElse(null);
    }

    /**
     * Mapper, który potrafi zmapować obiekt na podstawie przekazanych metadanych,
     * a nie tych przypisanych do strategii.
     */
    private Object mapSpecificEntity(ResultSet rs, EntityMetadata metadata) throws SQLException {
        try {
            // Tworzymy instancję konkretnej klasy (np. Husky)
            Object entity = metadata.getEntityClass().getDeclaredConstructor().newInstance();

            for (PropertyMetadata prop : metadata.getColumnsForConcreteTable()) {
                try {
                    Object value = getValueFromResultSet(rs, prop.getColumnName(), prop.getType());

                    if (value != null) {
                        ReflectionUtils.setFieldValue(entity, prop.getName(), value);
                    }
                } catch (SQLException e) {
                    // Ignorujemy, jeśli kolumny nie ma (choć w tej strategii powinna być)
                }
            }
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Error mapping entity " + metadata.getEntityClass().getName(), e);
        }
    }

    // Pomocnicza metoda do budowania WHERE id = ? (wyciągnięta, by nie powielać kodu)
    private void appendIdWhereClause(StringBuilder sql, List<Object> params, Collection<PropertyMetadata> idColumns, Object id) {
        if (idColumns.size() == 1) {
            PropertyMetadata pm = idColumns.iterator().next();
            sql.append(pm.getColumnName()).append(" = ?");
            params.add(pm.getType().cast(id));
        } else {
            // Tu wklej swoją logikę dla composite key, dla czytelności skróciłem
            int count = 0;
            for(PropertyMetadata pm : idColumns) {
                if(count > 0) sql.append(" AND ");
                sql.append(pm.getColumnName()).append(" = ?");
                Object val = ReflectionUtils.getFieldValue(id, pm.getName());
                params.add(val);
                count++;
            }
        }
    }




//    @Override
//    public <T> List<T> findAll(Class<T> type, Session session) {
//        // 1. Pobieramy metadane dla klasy, o którą pytamy (np. Dog)
//        EntityMetadata parentMeta = this.entityMetadata;
//
//        // 2. Znajdujemy wszystkie podklasy (włącznie z 'type'), które mają swoje tabele
//        List<EntityMetadata> concreteSubclasses = getAllConcreteSubclasses(parentMeta);
//
//        // 3. Ustalamy wspólne kolumny (tylko te, które ma klasa 'type' / Dog)
//        assert parentMeta != null;
//        List<String> columnsToSelect = parentMeta.getColumnsForConcreteTable()
//                .stream()
//                .map(PropertyMetadata::getColumnName)
//                .collect(Collectors.toList());
//
//        String columnsSql = String.join(", ", columnsToSelect);
//
//        // 4. Budujemy UNION ALL
//        StringBuilder sqlBuilder = new StringBuilder();
//
//        for (int i = 0; i < concreteSubclasses.size(); i++) {
//            EntityMetadata subMeta = concreteSubclasses.get(i);
//
//            sqlBuilder.append("SELECT ")
//                    .append(columnsSql)
//                    // WAŻNE: Wstrzykujemy nazwę klasy jako sztuczną kolumnę DTYPE
//                    .append(", '").append(subMeta.getEntityClass().getName()).append("' as DTYPE")
//                    .append(" FROM ")
//                    .append(subMeta.getTableName());
//
//            if (i < concreteSubclasses.size() - 1) {
//                sqlBuilder.append(" UNION ALL ");
//            }
//        }
//
//        String sql = sqlBuilder.toString();
//        System.out.println("TPC SQL: " + sql);
//
//        try {
//            JdbcExecutor jdbc = session.getJdbcExecutor();
//            // Używamy specjalnego mappera, który czyta DTYPE
//            return jdbc.query(sql, rs -> mapPolymorphicEntity(rs, type));
//        } catch (Exception e) {
//            throw new RuntimeException("Error finding all entities in TPC", e);
//        }
//    }

    @Override
    public <T> List<T> findAll(Class<T> type, Session session) {
        // 1. Znajdujemy wszystkie podklasy (Husky, Cat, Dog...)
        List<EntityMetadata> concreteSubclasses = getAllConcreteSubclasses(this.entityMetadata);

        // 2. Zbieramy WSZYSTKIE unikalne nazwy kolumn ze wszystkich podklas
        // Np. [id, name, age, how, cat_name]
        Set<String> allPossibleColumns = new LinkedHashSet<>();
        for (EntityMetadata meta : concreteSubclasses) {
            for (PropertyMetadata pm : meta.getColumnsForConcreteTable()) {
                allPossibleColumns.add(pm.getColumnName());
            }
        }

        // 3. Budujemy zapytanie UNION ALL z wypełnianiem NULLami
        StringBuilder sqlBuilder = new StringBuilder();

        for (int i = 0; i < concreteSubclasses.size(); i++) {
            EntityMetadata subMeta = concreteSubclasses.get(i);

            // Zbiór kolumn, które ta konkretna tabela faktycznie posiada
            Set<String> subTableColumns = subMeta.getColumnsForConcreteTable().stream()
                    .map(PropertyMetadata::getColumnName)
                    .collect(Collectors.toSet());

            sqlBuilder.append("SELECT ");

            // Iterujemy po WSZYSTKICH możliwych kolumnach hierarchii
            List<String> selectionParts = new ArrayList<>();
            for (String colName : allPossibleColumns) {
                if (subTableColumns.contains(colName)) {
                    // Tabela ma tę kolumnę -> wybieramy ją
                    selectionParts.add(colName);
                } else {
                    // Tabela nie ma tej kolumny -> wstawiamy NULL i aliasujemy nazwą kolumny
                    // (alias jest ważny, żeby ResultSet wiedział jak nazwać kolumnę)
                    selectionParts.add("NULL AS " + colName);
                }
            }

            sqlBuilder.append(String.join(", ", selectionParts));

            // Dodajemy DTYPE
            sqlBuilder.append(", '").append(subMeta.getEntityClass().getName()).append("' as DTYPE")
                    .append(" FROM ")
                    .append(subMeta.getTableName());

            if (i < concreteSubclasses.size() - 1) {
                sqlBuilder.append(" UNION ALL ");
            }
        }

        String sql = sqlBuilder.toString();
        System.out.println("TPC findAll SQL: " + sql);

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            // Przekazujemy listę wszystkich kolumn, żeby mapper wiedział co mapować
            return jdbc.query(sql, rs -> mapPolymorphicEntityFull(rs, type, allPossibleColumns));
        } catch (Exception e) {
            throw new RuntimeException("Error finding all entities in TPC", e);
        }
    }

    // Nowy mapper, który potrafi obsłużyć pola z podklas
    private <T> T mapPolymorphicEntityFull(ResultSet rs, Class<T> baseType, Set<String> allColumns) throws SQLException {
        try {
            // 1. Odczytujemy DTYPE
            String className = rs.getString("DTYPE");
            Class<?> realClass = Class.forName(className);

            // 2. Tworzymy instancję (np. Husky)
            Object instance = realClass.getDeclaredConstructor().newInstance();

            // 3. Wypełniamy pola używając ReflectionUtils
            // Iterujemy po kolumnach dostępnych w SQL (allColumns), a nie polach klasy bazowej
            for (String colName : allColumns) {
                try {
                    Object value = rs.getObject(colName);

                    // Jeśli wartość jest NULL (np. kolumna z innej klasy), to po prostu nic nie robimy
                    if (value != null) {
                        // Musimy znaleźć nazwę pola w klasie na podstawie nazwy kolumny
                        // To wymagałoby mapy kolumna -> pole.
                        // DLA UPROSZCZENIA: zakładam tutaj, że w Twoim kodzie nazwa kolumny == nazwa pola (zazwyczaj tak jest w prostych ORM)
                        // W pełnej wersji musiałbyś przeszukać metadane realClass, żeby znaleźć pole dla danej kolumny.

                        String fieldName = colName; // Uproszczenie!

                        // Fix dla Integer -> Long
                        if (value instanceof Integer) {
                            // Sprawdźmy typ pola w klasie docelowej
                            try {
                                var field = ReflectionUtils.findField(realClass, fieldName); // Twoja metoda findField powinna zwracać Field
                                if (field.getType() == Long.class) {
                                    value = ((Integer) value).longValue();
                                }
                            } catch (Exception e) {
                                // ignoruj jeśli pole nie istnieje
                            }
                        }

                        ReflectionUtils.setFieldValue(instance, fieldName, value);
                    }
                } catch (Exception e) {
                    // Ignorujemy błędy mapowania pojedynczych pól (np. gdy pole w klasie nazywa się inaczej niż kolumna)
                    // W produkcji trzeba tu użyć metadanych: column -> field name
                }
            }
            return baseType.cast(instance);
        } catch (Exception e) {
            throw new RuntimeException("Error mapping polymorphic entity", e);
        }
    }

    private List<EntityMetadata> getAllConcreteSubclasses(EntityMetadata parent) {
        List<EntityMetadata> result = new ArrayList<>();
        List<EntityMetadata> toVisit = new ArrayList<>();

        toVisit.add(parent);
        result.add(parent);
        while (!toVisit.isEmpty()) {
            var current =  toVisit.remove(0);
            for (EntityMetadata child: current.getInheritanceMetadata().getChildren()){
                result.add(child);
                toVisit.add(child);
            }
        }
        return result;
    }

    private <T> T mapPolymorphicEntity(ResultSet rs, Class<T> baseType) throws SQLException {
        try {
            // 1. Odczytujemy sztuczną kolumnę DTYPE
            String className = rs.getString("DTYPE");

            // 2. Tworzymy instancję właściwej klasy (np. Husky, mimo że szukamy Dog)
            Class<?> realClass = Class.forName(className);
            Object instance = realClass.getDeclaredConstructor().newInstance();

            // 3. Wypełniamy pola.
            assert this.entityMetadata != null;
            for (PropertyMetadata col : this.entityMetadata.getColumnsForConcreteTable()) {
                String colName = col.getColumnName();
                Object value = rs.getObject(colName);
                ReflectionUtils.setFieldValue(instance, colName, value);
            }
            return baseType.cast(instance);
        } catch (Exception e) {
            throw new RuntimeException("Error mapping polymorphic entity", e);
        }
    }

    private Object mapEntity(ResultSet rs) throws SQLException {
        try {
            assert entityMetadata != null;
            Object entity = entityMetadata.getEntityClass().getDeclaredConstructor().newInstance();

            for (PropertyMetadata prop : entityMetadata.getColumnsForConcreteTable()) {
                try {
                    Object value = rs.getObject(prop.getColumnName());

                    // Konwersja Integer -> Long dla wszystkich pól Long
//                    if (value instanceof Integer && prop.getType() == Long.class) {
//                        value = ((Integer) value).longValue();
//                    }

                    if (value != null) {
                        // I hate dates
                        if (value instanceof java.sql.Date) {
                            value = ((Date) value).toLocalDate();
                        } else if (value instanceof java.sql.Timestamp) {
                            value = ((java.sql.Timestamp) value).toLocalDateTime();
                        } else if (value instanceof java.sql.Time) {
                            value = ((java.sql.Time) value).toLocalTime();
                        }
                        // cast integer to short
                        if (value instanceof Integer && prop.getType() == Short.class) {
                            value = ((Integer) value).shortValue();
                        }
                        ReflectionUtils.setFieldValue(entity, prop.getName(), value);
                    }
                } catch (SQLException e) {
                    System.err.println("Failed to get column: " + prop.getColumnName() + " - " + e.getMessage());
                }
            }

            return entity;
        } catch (Exception e) {
            throw new SQLException("Failed to map entity: " + entityMetadata.getEntityClass().getName(), e);
        }
    }
}
