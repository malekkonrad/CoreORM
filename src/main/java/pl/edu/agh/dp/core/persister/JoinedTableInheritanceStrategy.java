package pl.edu.agh.dp.core.persister;

import javafx.util.Pair;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.*;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class JoinedTableInheritanceStrategy extends AbstractInheritanceStrategy {

    public JoinedTableInheritanceStrategy(EntityMetadata entityMetadata) {
        super(entityMetadata);
    }

    @Override
    public PairTargetStatements getPairStatement(Object entity, String relationshipName) {
        assert entityMetadata != null;
        AssociationMetadata associationMetadata = entityMetadata.getAssociationMetadata().get(relationshipName);

        TargetStatement joinStmt = associationMetadata.getJoinStatement();
        TargetStatement whereStmt = entityMetadata.getSelectByIdStatement(entity);
//        // joined root is the actual root
        whereStmt.setTargetTableName(entityMetadata.getInheritanceMetadata().getRootClass().getTableName());

        return new PairTargetStatements(whereStmt, joinStmt);
    }

    @Override
    public Pair<String, String> create() {
        StringBuilder sb = new StringBuilder();

        assert entityMetadata != null;
//        sb.append("CREATE TABLE ").append(entityMetadata.getTableName()).append(" (\n");
//
//        List<String> columnDefs = new ArrayList<>();
//        List<String> primaryKeys = new ArrayList<>();
//
//        // IDs first because POSTGRES fuck you
//        for (PropertyMetadata prop : entityMetadata.getProperties().values()) {
//            if (prop.isId()) {
//                columnDefs.add(prop.toSqlColumn());
//                primaryKeys.add(prop.getColumnName());
//            }
//        }
//
//        // Add columns defined in this class only (not inherited ones)
//        for (PropertyMetadata prop : entityMetadata.getProperties().values()) {
//            if (prop.isId()) {
//                continue;
//            }
//            columnDefs.add(prop.toSqlColumn());
//        }
//
//        sb.append(String.join(",\n", columnDefs));
//
//        // Add primary key constraint - for root
//        if (!primaryKeys.isEmpty()) {
//            sb.append(",\n PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
//        }
//
//        // Add foreign key to parent table if this is a child entity
//        InheritanceMetadata inhMeta = entityMetadata.getInheritanceMetadata();
//        // change not parent but root - root must have id, but this id will point to parent
//        if (inhMeta.getRootClass() != null && !inhMeta.isRoot()) {
//            EntityMetadata root = inhMeta.getRootClass();
//
//            if (inhMeta.getParent() != null) {
//                EntityMetadata parent = inhMeta.getParent();
//
//                // Foreign key references parent's primary key
//                Map<String, PropertyMetadata> rootIds = root.getIdColumns();
//                for (PropertyMetadata prop : rootIds.values()) {
//                    entityMetadata.addIdProperty(prop);
//                }
//                List<String> fkColumns = new ArrayList<>();
//                List<String> refColumns = new ArrayList<>();
//
//                for (PropertyMetadata idProp : rootIds.values()) {
//                    fkColumns.add(idProp.getColumnName());
//                    refColumns.add(idProp.getColumnName());
//                }
//
//                // adding ids to parent and root table
//                for (String fkColumn : fkColumns) {
//                    String sqlType = root.getIdColumns().get(fkColumn).getSqlType();
//                    sb.append(",\n    ").append(fkColumn).append(" ").append(sqlType);
//                }
//
//                sb.append(",\n    PRIMARY KEY (")
//                        .append(String.join(", ", fkColumns))
//                        .append(")");
//
//                sb.append(",\n    FOREIGN KEY (")
//                        .append(String.join(", ", fkColumns))
//                        .append(") REFERENCES ")
//                        .append(parent.getTableName())
//                        .append(" (")
//                        .append(String.join(", ", refColumns))
//                        .append(")");
//            }
//        }
//        sb.append("\n);");

        sb.append(entityMetadata.getSqlTable());

        return new Pair<>(sb.toString(), entityMetadata.getSqlConstraints());
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

            List<PropertyMetadata> idColumns = new ArrayList<>(root.getIdColumns().values());
            // get provided ids
            Set<String> idProvided = getProvidedIds(entity);
            // composite keys error handling
            String idProp = getIdNameAndCheckCompositeKey(idProvided, idColumns);

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

                // relationships
                fillRelationshipData(entity, meta, columns, values);

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

                Long currentResult = jdbc.insert(sql.toString(), isRoot ? idProp : "", values.toArray());

                if (isRoot) {
                    boolean hasAutoIncrementId = meta.getIdColumns().values().stream().anyMatch(PropertyMetadata::isAutoIncrement);

                    if (hasAutoIncrementId) {
                        generatedId = currentResult;

                        for (PropertyMetadata idPropName : meta.getIdColumns().values()) {
                            ReflectionUtils.setFieldValue(entity, idPropName.getName(), generatedId);
                        }
                    } else {
                        String idPropName = meta.getIdColumns().values().iterator().next().getName();
                        generatedId = (Long) ReflectionUtils.getFieldValue(entity, idPropName);
                    }
                }
            }

            // association tables
            insertAssociationTables(jdbc, entity);

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

                    setColumns.add(prop.getColumnName());
                    Object value = ReflectionUtils.getFieldValue(entity, prop.getName());
                    values.add(value);
                }

                fillRelationshipData(entity, meta, setColumns, values);

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
                        .append(" SET ").append(String.join(" = ?, ", setColumns)).append(setColumns.isEmpty() ? "" : " = ?")
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
            assert entityMetadata != null;
            // 1. Budujemy potężne zapytanie z LEFT JOINami do wszystkich podklas
            SqlAndParams query = buildPolymorphicQuery(id); // id != null -> generuje WHERE

            System.out.println("Joined findById SQL: " + query.sql);

            JdbcExecutor jdbc = session.getJdbcExecutor();
            return jdbc.queryOne(query.sql, this::mapRow, query.params.toArray()).orElse(null);

        } catch (Exception e) {
            throw new RuntimeException("Error finding entity with id = " + id, e);
        }
    }

    @Override
    public <T> List<T> findAll(Class<T> type, Session session, PairTargetStatements pairTargetStatements) {
        TargetStatement joinStmt = pairTargetStatements.getJoinStatements().get(0);
        TargetStatement whereStmt = pairTargetStatements.getWhereStatements().get(0);
        assert entityMetadata != null;
        try {
            // 1. Budujemy to samo zapytanie, ale bez WHERE id = ?
            SqlAndParams query = buildPolymorphicQuery(null);

            // Dodajemy join statement
            query.sql += " " + joinStmt.getStatement();

            // additional where
            if (!whereStmt.isBlank()) {

                query.sql += " WHERE " + whereStmt.getStatement();
            }

            System.out.println("Joined findAll SQL: " + query.sql);

            JdbcExecutor jdbc = session.getJdbcExecutor();

            // 2. Mapujemy wyniki
            List<Object> results = jdbc.query(query.sql, this::mapRow);

            // 3. Filtrujemy po Javie (bo pobraliśmy całą hierarchię)
            // Można by optymalizować dodając WHERE DTYPE IN (...), ale przy Joined
            // i tak musimy joinować tabele, więc filtrowanie w pamięci jest akceptowalne na tym etapie.
            List<T> filtered = new ArrayList<>();
            for (Object obj : results) {
                if (type.isInstance(obj)) {
                    filtered.add(type.cast(obj));
                }
            }
            return filtered;

        } catch (Exception e) {
            throw new RuntimeException("Error finding entities in Joined Strategy", e);
        }
    }

    private static class SqlAndParams {
        String sql;
        List<Object> params;

        public SqlAndParams(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    private SqlAndParams buildPolymorphicQuery(Object id) {
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        List<EntityMetadata> allSubclasses = getAllSubclassesIncludingRoot(root);

        StringBuilder selectPart = new StringBuilder("SELECT ");
        StringBuilder joinPart = new StringBuilder(" FROM " + root.getTableName());

        List<String> columns = new ArrayList<>();

        // 1. Generujemy listę kolumn z aliasami dla WSZYSTKICH tabel w hierarchii
        // Format aliasu: tabela_kolumna (np. animals_name, dogs_how)
        for (EntityMetadata meta : allSubclasses) {
            for (PropertyMetadata prop : meta.getProperties().values()) {
                // Pomijamy pola, które nie są w bazie (np. relacje OneToMany, jeśli nie są owning side)
                if (prop.getSqlType() != null) {
                    columns.add(meta.getTableName() + "." + prop.getColumnName() +
                            " AS " + meta.getTableName() + "_" + prop.getColumnName());
                }
            }
        }

        // Dodajemy DTYPE explicite (chociaż pętla wyżej powinna go złapać, jeśli jest w PropertyMetadata)
        // Zakładam, że DTYPE jest w root i nazywa się "DTYPE"
        // Jeśli masz DTYPE jako PropertyMetadata, to pętla wyżej to ogarnęła.
        // Jeśli to "ukryta" kolumna, trzeba dodać:
        // columns.add(root.getTableName() + ".DTYPE AS discriminator");

        selectPart.append(String.join(", ", columns));

        // 2. Generujemy LEFT JOINy dla każdej podklasy
        // Root jest już w FROM. Lecimy po reszcie.
        for (EntityMetadata sub : allSubclasses) {
            if (sub == root) continue;

            EntityMetadata parent = sub.getInheritanceMetadata().getParent();
            // JOIN po ID
            // Zakładam, że klucz główny ma tę samą nazwę kolumny w dziecku i rodzicu (standard w JPA)
            // Jeśli nie, trzeba pobrać nazwę kolumny ID dla każdej tabeli osobno.
            String pkName = root.getIdColumns().values().iterator().next().getColumnName();

            joinPart.append(" LEFT JOIN ").append(sub.getTableName())
                    .append(" ON ")
                    .append(parent.getTableName()).append(".").append(pkName)
                    .append(" = ")
                    .append(sub.getTableName()).append(".").append(pkName);
        }

        // 3. WHERE (opcjonalnie)
        List<Object> params = new ArrayList<>();
        if (id != null) {
            String pkName = root.getIdColumns().values().iterator().next().getColumnName();
            joinPart.append(" WHERE ").append(root.getTableName()).append(".").append(pkName).append(" = ?");

            // Fix na typ ID (Integer vs Long)
            PropertyMetadata idProp = root.getIdColumns().values().iterator().next();
            if (idProp.getType() == Long.class && id instanceof Integer) {
                params.add(((Integer) id).longValue());
            } else {
                params.add(id);
            }
        }

        return new SqlAndParams(selectPart.toString() + joinPart.toString(), params);
    }

    // --- Core Logic: Mapowanie ---

    private Object mapRow(ResultSet rs) throws SQLException {
        try {
            assert entityMetadata != null;
            EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();

            // 1. Pobierz DTYPE z tabeli ROOT
            // Musimy wiedzieć, jak nazywa się kolumna dyskryminatora. Domyślnie "DTYPE".
            // Ale uwaga: użyliśmy aliasów! Więc szukamy "animals_DTYPE".
            String discriminatorCol = "DTYPE"; // Lub weź z metadanych
            String alias = root.getTableName() + "_" + discriminatorCol;

            String className;
            try {
                className = rs.getString(alias);
            } catch (SQLException e) {
                // Fallback: jeśli nie znaleziono aliasu, może spróbujmy bezpośrednio (mało prawdopodobne przy tej strategii)
                className = rs.getString(discriminatorCol);
            }

            if (className == null) {
                // Jeśli DTYPE jest null, to zakładamy, że to instancja ROOT (o ile root nie jest abstrakcyjny)
                className = root.getEntityClass().getName();
            }

            // 2. Znajdź klasę w mapie (O(1) zamiast iteracji)
            Class<?> realClass;
            try {
                // Mapa w InheritanceMetadata powinna mapować "Husky" -> Husky.class
                // Jeśli mapa trzyma SimpleName, a w bazie jest FullName, trzeba uważać.
                // Zakładam, że w bazie trzymasz to co masz w mapie (np. discriminator value).
                realClass = root.getInheritanceMetadata().getDiscriminatorToClass().get(className);

                // Fallback jeśli mapa zawiedzie (np. w bazie jest pełna nazwa klasy)
                if (realClass == null) {
                    realClass = Class.forName(className);
                }
            } catch (Exception e) {
                throw new RuntimeException("Unknown entity type: " + className);
            }

            // 3. Utwórz instancję
            Object instance = realClass.getDeclaredConstructor().newInstance();

            // 4. Wypełnij pola (idąc w górę hierarchii dla TEJ KONKRETNEJ KLASY)
            populateFieldsWithAliases(instance, rs, realClass);

            return instance;

        } catch (Exception e) {
            throw new RuntimeException("Error mapping row in Joined strategy", e);
        }
    }

    private void populateFieldsWithAliases(Object instance, ResultSet rs, Class<?> realClass) throws SQLException {
        // Znajdź metadane dla konkretnej klasy (Husky)
        // Tutaj warto mieć mapę Class -> EntityMetadata w kontekście, żeby nie szukać,
        // ale na razie użyjmy Twojej metody pomocniczej (zoptymalizowanej)
        EntityMetadata currentMeta = findMetadataForClass(realClass);

        // Idziemy w górę: Husky -> Dog -> Animal
        while (currentMeta != null) {
            for (PropertyMetadata prop : currentMeta.getProperties().values()) {
                // Pomijamy pola nie-bazodanowe
                if (prop.getSqlType() == null) continue;

                if (prop.getName() == null) {
                    continue;
                }

                if (Objects.equals(prop.getColumnName(), "DTYPE")) continue; // FIXME this is so dirty

                String columnAlias = currentMeta.getTableName() + "_" + prop.getColumnName();

                try {
                    Object value = getValueFromResultSet(rs, columnAlias, prop.getType());

                    if (value != null) {
                        ReflectionUtils.setFieldValue(instance, prop.getName(), value);
                    }
                } catch (SQLException e) {
                    // Ignoruj brak kolumny (teoretycznie przy SELECT * z aliasami nie powinno się zdarzyć,
                    // ale przy LEFT JOIN wartości mogą być NULL, co getObject obsłuży,
                    // błąd poleci tylko jak nazwa aliasu jest błędna).
                }
            }

            // Idź do rodzica
            if (currentMeta.getInheritanceMetadata().getParent() != null) {
                currentMeta = currentMeta.getInheritanceMetadata().getParent();
            } else {
                currentMeta = null;
            }
        }
    }

    // Pomocnicza - zbiera wszystkie metadane (płaska lista)
    private List<EntityMetadata> getAllSubclassesIncludingRoot(EntityMetadata root) {
        List<EntityMetadata> list = new ArrayList<>();
        list.add(root);

        List<EntityMetadata> toProcess = new ArrayList<>(root.getInheritanceMetadata().getChildren());
        while (!toProcess.isEmpty()) {
            EntityMetadata current = toProcess.remove(0);
            list.add(current);
            toProcess.addAll(current.getInheritanceMetadata().getChildren());
        }
        return list;
    }

    // Twoja metoda findMetadataForClass była OK, tylko upewnij się, że działa poprawnie
    private EntityMetadata findMetadataForClass(Class<?> clazz) {
        EntityMetadata root = entityMetadata.getInheritanceMetadata().getRootClass();
        if (root.getEntityClass().equals(clazz)) return root;

        // BFS search
        List<EntityMetadata> queue = new ArrayList<>(root.getInheritanceMetadata().getChildren());
        while(!queue.isEmpty()) {
            EntityMetadata current = queue.remove(0);
            if (current.getEntityClass().equals(clazz)) return current;
            queue.addAll(current.getInheritanceMetadata().getChildren());
        }
        return null;
    }


}
