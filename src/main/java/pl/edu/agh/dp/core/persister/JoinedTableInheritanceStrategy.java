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

        assert entityMetadata != null;
        sb.append("CREATE TABLE ").append(entityMetadata.getTableName()).append(" (\n");

        List<String> columnDefs = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();

        // IDs first because POSTGRES fuck you
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

        // Add primary key constraint - for root
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
        while(true) {
            assert current != null;
            if (current.getInheritanceMetadata().getParent() == null) break;
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
            // Sorted visiting chain from root to leaf
            List<EntityMetadata> visitingChain = buildInheritanceChain();

            assert entityMetadata != null;
            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();

            for (EntityMetadata meta : visitingChain) {
                List<String> columns = new ArrayList<>();
                List<Object> values = new ArrayList<>();

                boolean isRoot = meta.getInheritanceMetadata().isRoot();

                for (PropertyMetadata prop : meta.getProperties().values()) {

                    if (!fieldBelongsToClass(prop, meta.getEntityClass())) {
                        continue;
                    }

                    //ID support in ROOT table (AutoIncrement)
                    if (isRoot && prop.isId() && prop.isAutoIncrement()) {
                        continue;
                    }

                    columns.add(prop.getColumnName());
                    Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
                    values.add(value);
                }

                if (isRoot) {
                    columns.add("DTYPE");
                    values.add(root.getInheritanceMetadata().getClassToDiscriminator().get(entityMetadata.getEntityClass()));
                }

                // ID not in root
                if (!isRoot) {
                    if (generatedId == null) {
                        throw new RuntimeException("Generated ID is null but trying to insert into child table! Root insert failed?");
                    }

                    String childIdColumnName = root.getIdColumns().keySet().iterator().next();
                    columns.add(childIdColumnName);
                    values.add(generatedId);
                }

                StringBuilder sql = new StringBuilder();
                sql.append("INSERT INTO ")
                        .append(meta.getTableName())
                        .append(" (")
                        .append(String.join(", ", columns))
                        .append(") VALUES (")
                        .append("?,".repeat(values.size()));

                sql.deleteCharAt(sql.length() - 1); // delete last comma
                sql.append(")");

                System.out.println("Joined Insert SQL (" + (isRoot ? "ROOT" : "CHILD") + "): " + sql);
                System.out.println("Values: " + values);

                Long currentResult = jdbc.insert(sql.toString(), values.toArray());

                if (isRoot) {
                    boolean hasAutoIncrementId = meta.getIdColumns().values().stream().anyMatch(PropertyMetadata::isAutoIncrement);

                    if (hasAutoIncrementId) {
                        generatedId = currentResult;

                        for (PropertyMetadata idProp : meta.getIdColumns().values()) {
                            ReflectionUtils.setFieldValue(entity, idProp.getName(), generatedId);
                        }
                    } else {
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

                assert entityMetadata != null;
                EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
                Object idValue = getIdValue(entity);
                String whereClause = buildWhereClause(root);
                Object[] idParams = prepareIdParams(idValue);

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

            //DELETE from the farthest child to the root (to avoid affecting foreign keys)
            Collections.reverse(chain);

            assert entityMetadata != null;
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
            String sql = buildJoinQuery();

            // Get ID column name from root
            assert entityMetadata != null;
            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
            PropertyMetadata idColumn = root.getIdColumns().values().iterator().next();

            sql += " WHERE " + root.getTableName() + "." + idColumn.getColumnName() + " = ?";

            System.out.println("Joined FindById SQL: " + sql);

            return jdbc.queryOne(sql, this::mapEntity, id).orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Error finding entity with id = " + id, e);
        }
    }

    @Override
    public <T> List<T> findAll(Class<T> type, Session session) {
        assert this.entityMetadata != null;
        EntityMetadata rootMetadata = this.entityMetadata.getInheritanceMetadata().getRootClass();

        String sql = buildPolymorphicJoinQuery(this.entityMetadata, rootMetadata);
        System.out.println("Joined SQL: " + sql);

        try {
            JdbcExecutor jdbc = session.getJdbcExecutor();

            // We map the results using the DTYPE column from the ROOT table
            List<Object> results = jdbc.query(sql, rs -> mapJoinedEntity(rs, rootMetadata));

            // We filter and cast (Polymorphism will work automatically)
            List<T> filtered = new ArrayList<>();
            for (Object obj : results) {
                // IMPORTANT: We use isInstance, not equals!
                // This will ensure that Husky passes when we search for Dog.
                if (type.isInstance(obj)) {
                    filtered.add(type.cast(obj));
                }
            }
            return filtered;

        } catch (Exception e) {
            throw new RuntimeException("Error finding entities in Joined Strategy", e);
        }
    }

    private String buildPolymorphicJoinQuery(EntityMetadata targetMeta, EntityMetadata rootMeta) {
        StringBuilder sb = new StringBuilder();

        String rootTable = rootMeta.getTableName(); // np. "animals"
        String targetTable = targetMeta.getTableName(); // np. "dogs"

        // 1. SELECT: Musimy wybrać kolumny ze wszystkich tabel w hierarchii (od Roota do Liści)
        // Dla uproszczenia tutaj daję *, ale w produkcji powinno się wypisać aliasy t0.col, t1.col...
        // żeby uniknąć konfliktu nazw (np. jeśli Animal i Dog mają kolumnę o tej samej nazwie).
        sb.append("SELECT * FROM ").append(rootTable);

        // 2. JOINY DO KLASY DOCELOWEJ (INNER JOIN)
        // Jeśli szukamy Dog, musimy zrobić INNER JOIN z dogs.
        // Jeśli szukamy Animal, ten krok jest pomijany (bo rootTable == targetTable).

        if (!rootMeta.equals(targetMeta)) {
            // Musisz przejść po hierarchii w dół aż do targetMeta
            // Zakładam uproszczenie: targetMeta wie jaka jest jego tabela
            sb.append(" INNER JOIN ").append(targetTable)
                    .append(" ON ").append(rootTable).append(".id = ").append(targetTable).append(".id");
        }

        // 3. JOINY DO PODKLAS (LEFT JOIN)
        // Musimy dołączyć Husky, żeby pobrać pole 'how'.
        // LEFT JOIN, bo jeśli to zwykły pies, to w tabeli 'huskys' nie ma rekordu.

        List<EntityMetadata> subclasses = getAllSubclasses(targetMeta); // Musisz mieć taką metodę
        for (EntityMetadata sub : subclasses) {
            String subTable = sub.getTableName();
            sb.append(" LEFT JOIN ").append(subTable)
                    .append(" ON ").append(targetTable).append(".id = ").append(subTable).append(".id");
        }

        // 4. WHERE (Dla pewności, choć INNER JOIN wyżej już to załatwił)
        // W strategii Joined zazwyczaj Root ma kolumnę DTYPE.
        // Możemy filtrować po DTYPE, ale INNER JOIN na tabeli 'dogs' jest wystarczający i szybszy.

        return sb.toString();
    }

    private Object mapJoinedEntity(ResultSet rs, EntityMetadata rootMetadata) throws SQLException {
        try {
            // 1. Odczytujemy DTYPE z tabeli ROOT (np. "pl.edu...Husky")
            // Zakładam, że kolumna nazywa się DTYPE lub masz jej nazwę w metadanych
            String discriminatorCol = rootMetadata.getInheritanceMetadata().getDiscriminatorColumnName();
            String className = rs.getString(discriminatorCol);

            Class<?> clazz = rootMetadata.getInheritanceMetadata().getDiscriminatorToClass().get(className);

//            clazz.toString()
            // 2. Tworzymy instancję
            Class<?> realClass = Class.forName(clazz.getName());
            Object instance = realClass.getDeclaredConstructor().newInstance();

            // 3. Wypełniamy pola
            // Musisz przejść po polach realClass (Husky) oraz wszystkich rodziców (Dog, Animal).
            // Ponieważ zrobiliśmy "SELECT *", ResultSet zawiera kolumny ze wszystkich połączonych tabel.

            populateAllFields(instance, rs, realClass);

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Mapping error in Joined Strategy", e);
        }
    }


    private EntityMetadata findThisFucker(Class<?> realClass){
        EntityMetadata tempRoot = entityMetadata.getInheritanceMetadata().getRootClass();
        var subs = getAllSubclasses(tempRoot);

        for (EntityMetadata sub : subs) {
            if (sub.getEntityClass().equals(realClass)) {
                return sub;
            }
        }
        return null;
    }

    private void populateAllFields(Object instance, ResultSet rs, Class<?> realClass) throws SQLException {
        // We get metadata for specific class (e.g. Husky)
        ///  WARNING najbardziej chujowe rozwiązanie, jakie świat widział
        EntityMetadata currentMetadata = findThisFucker(realClass);

        // 2. Iterujemy w górę hierarchii (Husky -> Dog -> Animal)
        // Musimy wypełnić pola z każdej warstwy dziedziczenia
        while (currentMetadata != null) {

            // Pobieramy definicje kolumn przypisane do tej konkretnej encji/tabeli
            for (PropertyMetadata column : currentMetadata.getColumnsForConcreteTable()) {
                String columnName = column.getColumnName(); // np. "how", "age", "name"
                String fieldName = column.getName();   // np. "how", "age", "name"

                if (fieldName == null) {
                    continue;
                }

                // Sprawdzamy czy pole należy do klasy (to już masz, ale warto zostawić)
                if (!fieldBelongsToClass(column, currentMetadata.getEntityClass())) {
                    continue;
                }

                try {
                    // 3. Pobieramy wartość z ResultSet
                    // Używamy getObject, żeby pobrać wartość bez względu na typ (Integer, String, etc.)
                    // Warto tutaj sprawdzić czy kolumna istnieje w RS, choć przy SELECT * powinna być.
                    Object value = rs.getObject(columnName);

                    // 4. Wpisujemy wartość do obiektu używając Twojego ReflectionUtils
                    // Ważne: Sprawdzamy null, żeby nie próbować wpisać null do typu prymitywnego (int)
                    if (value != null) {
                        ReflectionUtils.setFieldValue(instance, fieldName, value);
                    }
                } catch (SQLException e) {
                    // Czasami w ResultSet kolumna może nie istnieć (np. jeśli nie użyłeś SELECT * tylko wymieniłeś kolumny)
                    // Wtedy ignorujemy lub logujemy błąd
                    System.err.println("Column not found in ResultSet: " + columnName);
                }
            }

            // 5. Przechodzimy do rodzica (Husky -> Dog, potem Dog -> Animal)
            if (currentMetadata.getInheritanceMetadata().getParent() != null) {
                currentMetadata = currentMetadata.getInheritanceMetadata().getParent();
            } else {
                currentMetadata = null; // Koniec hierarchii (doszliśmy do Object lub klasy bez adnotacji @Entity)
            }
        }
    }

    private List<EntityMetadata> getAllSubclasses(EntityMetadata root) {
        List<EntityMetadata> result = new ArrayList<>();
        Deque<EntityMetadata> stack = new ArrayDeque<>(root.getInheritanceMetadata().getChildren());
        while(!stack.isEmpty()){
            EntityMetadata current = stack.pop();
            result.add(current);
            stack.addAll(current.getInheritanceMetadata().getChildren());
        }
        return result;
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
            assert entityMetadata != null;
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
        assert entityMetadata != null;
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
        assert entityMetadata != null;
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
