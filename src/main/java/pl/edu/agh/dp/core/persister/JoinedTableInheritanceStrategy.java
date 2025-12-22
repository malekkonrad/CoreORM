package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.InheritanceMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class JoinedTableInheritanceStrategy extends AbstractInheritanceStrategy {

    public JoinedTableInheritanceStrategy(EntityMetadata entityMetadata) {
        super(entityMetadata);
    }

    @Override
    public String create(JdbcExecutor jdbcExecutor) {
        StringBuilder sb = new StringBuilder();

        // Create table for this entity
        sb.append("CREATE TABLE ").append(entityMetadata.getTableName()).append(" (\n");

        List<String> columnDefs = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();

        for (PropertyMetadata prop : entityMetadata.getProperties().values()) {
            if (prop.isId()) {
                columnDefs.add("    " + prop.getColumnName() + " " + prop.getSqlType());
                primaryKeys.add(prop.getColumnName());
            }
        }


        // Add columns defined in this class only (not inherited ones)
        for (PropertyMetadata prop : entityMetadata.getProperties().values()) {
            if (prop.isId()) {
                continue;
            }
            columnDefs.add("    " + prop.getColumnName() + " " + prop.getSqlType());
        }

        sb.append(String.join(",\n", columnDefs));

        // Add primary key constraint
        if (!primaryKeys.isEmpty()) {
            sb.append(",\n    PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
        }

        // Add foreign key to parent table if this is a child entity
        InheritanceMetadata inhMeta = entityMetadata.getInheritanceMetadata();
        // change not parent but root - root must have id, but this id will point to parent
        if (inhMeta.getRootClass() != null && !inhMeta.isRoot()) {
            EntityMetadata root = inhMeta.getRootClass();

            if (inhMeta.getParent() != null) {
                EntityMetadata parent = inhMeta.getParent();

                // Foreign key references parent's primary key
                Collection<PropertyMetadata> rootIds = root.getIdColumns().values();
                List<String> fkColumns = new ArrayList<>();
                List<String> refColumns = new ArrayList<>();

                for (PropertyMetadata idProp : rootIds) {
                    fkColumns.add(idProp.getColumnName());
                    refColumns.add(idProp.getColumnName());
                }

                // adding ids to parent and root table
                for (String fkColumn : fkColumns) {
                    String sqlType = root.getIdColumns().get(fkColumn).getSqlType();
                    sb.append(",\n    ").append(fkColumn).append(" ").append(sqlType);
                }

                sb.append(",\n    PRIMARY KEY (")
                        .append(String.join(", ", fkColumns))
                        .append(")");


                sb.append(",\n    FOREIGN KEY (")
                        .append(String.join(", ", fkColumns))
                        .append(") REFERENCES ")
                        .append(parent.getTableName())
                        .append(" (")
                        .append(String.join(", ", refColumns))
                        .append(")");
            }

        }

        sb.append("\n);");

        return sb.toString();
    }

    private List<EntityMetadata> buildInheritanceChain() {
        List<EntityMetadata> visitingChain =  new ArrayList<>();
        visitingChain.add(entityMetadata);

        EntityMetadata current = entityMetadata;
        while(current.getInheritanceMetadata().getParent() != null) {
            visitingChain.add(current.getInheritanceMetadata().getParent());
            current = current.getInheritanceMetadata().getParent();
        }
        Collections.reverse(visitingChain);

        return visitingChain;
    }

    @Override
    public Object insert(Object entity, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();

            Long generatedId = null;
            // Zakładam, że visitingChain jest posortowany: Root -> Child -> GrandChild
            List<EntityMetadata> visitingChain = buildInheritanceChain();

            // Pobieramy metadane korzenia (tam gdzie jest definicja ID)
            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();

            // Pobieramy nazwę kolumny ID z korzenia (do użycia w podklasach)
            String rootIdColumnName = root.getIdColumns().values().iterator().next().getColumnName();

            for (EntityMetadata meta : visitingChain) {
                List<String> columns = new ArrayList<>();
                List<Object> values = new ArrayList<>();

                boolean isRoot = meta.getInheritanceMetadata().isRoot();

                // Iterujemy po polach danej klasy
                for (PropertyMetadata prop : meta.getProperties().values()) {

                    // 1. Sprawdzamy czy pole należy do tej konkretnej klasy (żeby nie dublować pól z dziedziczenia)
                    if (!fieldBelongsToClass(prop, meta.getEntityClass())) {
                        continue;
                    }

                    // 2. Obsługa ID w tabeli ROOT (AutoIncrement)
                    if (isRoot && prop.isId() && prop.isAutoIncrement()) {
                        // ZMIANA: Jeśli to ROOT i AutoIncrement -> POMIJAMY w SQL.
                        // Baza sama nada numer, my go odczytamy po wykonaniu inserta.
                        continue;
                    }

                    // Dodajemy standardowe pola (nie-ID lub ID nie-automatyczne)
                    columns.add(prop.getColumnName());
                    Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
                    values.add(value);
                }

                // 3. Obsługa ID w tabelach PODRZĘDNYCH (nie-Root)
                if (!isRoot) {
                    if (generatedId == null) {
                        throw new RuntimeException("Generated ID is null but trying to insert into child table! Root insert failed?");
                    }

                    // WAŻNE: W tabelach podrzędnych (Joined) klucz główny jest też kluczem obcym do Roota.
                    // Musimy go dodać RĘCZNIE, używając ID wygenerowanego wcześniej.

                    // Używamy nazwy kolumny ID zdefiniowanej w tej tabeli (meta) lub z roota,
                    // zazwyczaj w JOINED nazwa kolumny ID jest taka sama lub mapowana.
                    // Tutaj biorę nazwę kolumny ID z bieżącej tabeli:
//                    String childIdColumnName = meta.getIdColumns().values().iterator().next().getColumnName();


                    // FIXME duże uproszczenie - łatwa zmiana chyba wziąć pierwszy element - i guess
                    String childIdColumnName = root.getIdColumns().get("id").getColumnName();
                    columns.add(childIdColumnName);
                    values.add(generatedId);
                }

                if (columns.isEmpty()) {
                    // Może się zdarzyć, że tabela podrzędna nie ma własnych pól poza ID,
                    // ale i tak musimy zrobić INSERT samego ID, żeby zachować spójność dziedziczenia.
                    if (isRoot) continue; // Jeśli root jest pusty (dziwne), to skip
                }

                // Budowanie SQL
                StringBuilder sql = new StringBuilder();
                sql.append("INSERT INTO ")
                        .append(meta.getTableName())
                        .append(" (")
                        .append(String.join(", ", columns))
                        .append(") VALUES (")
                        .append("?,".repeat(values.size()));

                if (values.size() > 0) {
                    sql.deleteCharAt(sql.length() - 1); // usuń ostatni przecinek
                }
                sql.append(")");

                System.out.println("Joined Insert SQL (" + (isRoot ? "ROOT" : "CHILD") + "): " + sql);
                System.out.println("Values: " + values);

                // Wykonanie SQL
                Long currentResult = jdbc.insert(sql.toString(), values.toArray());

                // 4. Po insercie do ROOT - pobranie i ustawienie wygenerowanego ID
                if (isRoot) {
                    // Jeśli mieliśmy autoincrement, baza zwróciła nam nowe ID
                    // Jeśli nie, to currentResult może być null lub 0 (zależy od implementacji jdbc),
                    // ale wtedy ID braliśmy z obiektu.

                    boolean hasAutoIncrementId = meta.getIdColumns().values().stream().anyMatch(PropertyMetadata::isAutoIncrement);

                    if (hasAutoIncrementId) {
                        generatedId = currentResult; // To jest nasze ID z bazy (np. 15)

                        // Ustawiamy to ID w obiekcie Java (Refleksja)
                        for (PropertyMetadata idProp : meta.getIdColumns().values()) {
                            ReflectionUtils.setFieldValue(entity, idProp.getName(), generatedId);
                        }
                    } else {
                        // Jeśli nie było auto-increment, to ID musiał być ustawiony w obiekcie przed insertem
                        // Musimy go pobrać, żeby użyć w tabelach podrzędnych
                        String idPropName = meta.getIdColumns().values().iterator().next().getName();
                        generatedId = (Long) ReflectionUtils.getFieldValue(entity, idPropName);
                    }
                }
            }

            return generatedId;

        } catch (Exception e) {
            throw new RuntimeException("Error inserting entity with joined table strategy: " + entity, e);
        }
    }

    @Override
    public void update(Object entity, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            List<EntityMetadata> chain = buildInheritanceChain();

            // UPDATE w każdej tabeli w hierarchii
            for (EntityMetadata meta : chain) {
                List<String> setColumns = new ArrayList<>();
                List<Object> values = new ArrayList<>();

                // Tylko pola zdefiniowane w tej konkretnej klasie
                for (PropertyMetadata prop : meta.getProperties().values()) {
                    if (!fieldBelongsToClass(prop, meta.getEntityClass())) {
                        continue;
                    }

                    if (prop.isId()) {
                        continue; // ID nie jest aktualizowane
                    }

                    setColumns.add(prop.getColumnName() + " = ?");
                    Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
                    values.add(value);
                }

                // Jeśli brak kolumn do aktualizacji, pomiń tę tabelę
                if (setColumns.isEmpty()) {
                    continue;
                }

                // WHERE clause (zawsze używamy ID z roota)
                EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
                Object idValue = getIdValue(entity);
                String whereClause = buildWhereClause(root);
                Object[] idParams = prepareIdParams(idValue);

                // Połącz parametry
                List<Object> allParams = new ArrayList<>(values);
                allParams.addAll(Arrays.asList(idParams));

                StringBuilder sql = new StringBuilder();
                sql.append("UPDATE ").append(meta.getTableName())
                        .append(" SET ").append(String.join(", ", setColumns))
                        .append(" WHERE ").append(whereClause);

                System.out.println("Joined UPDATE SQL (" + meta.getTableName() + "): " + sql);
                System.out.println("Values: " + allParams);

                jdbc.update(sql.toString(), allParams.toArray());
            }

        } catch (Exception e) {
            throw new RuntimeException("Error updating entity with joined table strategy: " + entity, e);
        }
    }

    @Override
    public void delete(Object entity, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
            List<EntityMetadata> chain = buildInheritanceChain();

            // DELETE od najdalszego dziecka do roota (żeby nie naruszyć foreign keys)
            Collections.reverse(chain);

            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
            Object idValue = getIdValue(entity);
            Object[] idParams = prepareIdParams(idValue);

            for (EntityMetadata meta : chain) {
                String whereClause = buildWhereClause(root);

                StringBuilder sql = new StringBuilder();
                sql.append("DELETE FROM ").append(meta.getTableName())
                        .append(" WHERE ").append(whereClause);

                System.out.println("Joined DELETE SQL (" + meta.getTableName() + "): " + sql);
                System.out.println("ID: " + idValue);

                jdbc.update(sql.toString(), idParams);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error deleting entity with joined table strategy: " + entity, e);
        }
    }

    @Override
    public Object findById(Object id, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();
//
            // Build JOIN query across all tables in hierarchy
            String sql = buildJoinQuery();

            // Get ID column name from root
            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
            PropertyMetadata idColumn = root.getIdColumns().values().iterator().next();

            sql += " WHERE " + root.getTableName() + "." + idColumn.getColumnName() + " = ?";

            System.out.println("Joined FindById SQL: " + sql);

            return jdbc.queryOne(sql, this::mapEntity, id).orElse(null);
//            return null;
        } catch (Exception e) {
            throw new RuntimeException("Error finding entity with id = " + id, e);
        }
    }

    @Override
    public <T> List<T> findAll(Class<T> type, Session session) {
        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();

            // Build JOIN query
            String sql = buildJoinQuery();

            System.out.println("Joined FindAll SQL: " + sql);

            List<Object> results = jdbc.query(sql, this::mapEntity);

            // Filter by exact type
            List<T> filtered = new ArrayList<>();
            for (Object obj : results) {
                if (obj.getClass().equals(type)) {
                    filtered.add(type.cast(obj));
                }
            }

            return filtered;

        } catch (Exception e) {
            throw new RuntimeException("Error finding all entities of type: " + type, e);
        }
    }

    private String buildJoinQuery() {
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        List<EntityMetadata> chain = buildInheritanceChain();

        // SELECT columns from all tables
        List<String> selectColumns = new ArrayList<>();

        for (EntityMetadata meta : chain) {
            for (PropertyMetadata prop : meta.getProperties().values()) {
                if (fieldBelongsToClass(prop, meta.getEntityClass())) {
                    selectColumns.add(meta.getTableName() + "." + prop.getColumnName() +
                            " AS " + meta.getTableName() + "_" + prop.getColumnName());
                }
            }
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(String.join(", ", selectColumns));
        sql.append("\nFROM ").append(root.getTableName());

//        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();

        // Build JOINs from root down to current entity
        for (int i = 1; i < chain.size(); i++) {
            EntityMetadata child = chain.get(i);
            EntityMetadata parent = child.getInheritanceMetadata().getParent();


            // STUPID FIX but it works
            PropertyMetadata idProp = root.getIdColumns().values().iterator().next();

            sql.append("\nLEFT JOIN ").append(child.getTableName())
                    .append(" ON ").append(parent.getTableName()).append(".").append(idProp.getColumnName())
                    .append(" = ").append(child.getTableName()).append(".").append(idProp.getColumnName());
        }
        return sql.toString();
    }

    private Object mapEntity(ResultSet rs) throws SQLException {
        try {
            // Determine actual class from available data
            Class<?> actualClass = determineActualClass(rs);

            Object entity = actualClass.getDeclaredConstructor().newInstance();

            // Map fields from all tables in hierarchy
            List<EntityMetadata> chain = buildInheritanceChainForClass(actualClass);

            for (EntityMetadata meta : chain) {
                for (PropertyMetadata prop : meta.getProperties().values()) {
                    if (!fieldBelongsToClass(prop, meta.getEntityClass())) {
                        continue;
                    }

                    try {
                        String columnAlias = meta.getTableName() + "_" + prop.getColumnName();
                        Object value = getValueFromResultSet(rs, columnAlias, prop.getType());

                        if (value != null) {
                            ReflectionUtils.setFieldValue(entity, prop.getName(), value);
                        }
                    } catch (SQLException e) {
                        // Column might not exist if LEFT JOIN returned NULL
                    }
                }
            }

            return entity;

        } catch (Exception e) {
            throw new SQLException("Failed to map entity: " + entityMetadata.getEntityClass().getName(), e);
        }
    }

    private Class<?> determineActualClass(ResultSet rs) throws SQLException {
        // Check which tables have data by checking non-null child columns
        List<EntityMetadata> chain = buildInheritanceChain();

        // Start from most specific (leaf) and work up
        for (int i = chain.size() - 1; i >= 0; i--) {
            EntityMetadata meta = chain.get(i);

            // Check if any non-ID column from this table has data
            for (PropertyMetadata prop : meta.getProperties().values()) {
                if (!prop.isId() && fieldBelongsToClass(prop, meta.getEntityClass())) {
                    try {
                        String columnAlias = meta.getTableName() + "_" + prop.getColumnName();
                        Object value = rs.getObject(columnAlias);
                        if (value != null) {
                            return meta.getEntityClass();
                        }
                    } catch (SQLException e) {
                        // Continue checking
                    }
                }
            }
        }

        // Default to root class
        return entityMetadata.getInheritanceMetadata().getRootClass().getEntityClass();
    }

    private List<EntityMetadata> buildInheritanceChainForClass(Class<?> clazz) {
        // Find metadata for this class and build its chain
        EntityMetadata meta = findMetadataForClass(clazz);
        if (meta == null) {
            return Collections.emptyList();
        }

        List<EntityMetadata> chain = new ArrayList<>();
        EntityMetadata current = meta;

        while (current != null) {
            chain.add(0, current);
            current = current.getInheritanceMetadata().getParent();
        }

        return chain;
    }

    private EntityMetadata findMetadataForClass(Class<?> clazz) {
        // Search through hierarchy
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        if (root.getEntityClass().equals(clazz)) {
            return root;
        }

        Deque<EntityMetadata> queue = new ArrayDeque<>(root.getInheritanceMetadata().getChildren());
        while (!queue.isEmpty()) {
            EntityMetadata meta = queue.poll();
            if (meta.getEntityClass().equals(clazz)) {
                return meta;
            }
            queue.addAll(meta.getInheritanceMetadata().getChildren());
        }

        return null;
    }


}
