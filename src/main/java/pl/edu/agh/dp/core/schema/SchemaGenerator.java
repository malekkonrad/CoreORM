package pl.edu.agh.dp.core.schema;


import pl.edu.agh.dp.core.jdbc.ConnectionProvider;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;
import pl.edu.agh.dp.core.mapping.metadata.AssociationMetadata;
import pl.edu.agh.dp.core.mapping.metadata.EntityMetadata;
import pl.edu.agh.dp.core.mapping.metadata.PropertyMetadata;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Collection;

public class SchemaGenerator {

    private final MetadataRegistry registry;
    private final ConnectionProvider connectionProvider;

    public SchemaGenerator(MetadataRegistry registry,
                           ConnectionProvider connectionProvider) {
        this.registry = registry;
        this.connectionProvider = connectionProvider;
    }

    /**
     * Generuje schemat bazy na podstawie metadanych encji:
     *  - CREATE TABLE dla każdej encji
     *  - CREATE TABLE dla tabel pośrednich (ManyToMany) – opcjonalnie
     */
    public void generate() {
        Collection<EntityMetadata> entities = registry.getEntities().values();

        try (Connection con = connectionProvider.getConnection();
             Statement st = con.createStatement()) {

            // 1. Tworzymy tabele encji
            for (EntityMetadata meta : entities) {
                String sql = createTableSql(meta);
                st.executeUpdate(sql);
            }

            // 2.  tabele pośrednie ManyToMany
//            for (EntityMetadata meta : entities) {
//                for (AssociationMetadata assoc : meta.getAssociationMetadata()) {
//                    if (assoc.getType() == AssociationMetadata.Type.MANY_TO_MANY) {
//                        String joinSql = createJoinTableSql(meta, assoc);
//                        st.executeUpdate(joinSql);
//                    }
//                }
//            }

        } catch (Exception e) {
            throw new RuntimeException("Error while generating schema", e);
        }
    }

    private String createTableSql(EntityMetadata meta) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ")
                .append(meta.getTableName())
                .append(" (");

        // kolumna ID
        PropertyMetadata id = meta.getIdProperty();
        sb.append(id.getColumnName())
                .append(" ")
                .append(sqlTypeForId(id.getType()))
                .append(" PRIMARY KEY");

        // zwykłe kolumny
        for (PropertyMetadata pm : meta.getProperties()) {
            sb.append(", ")
                    .append(pm.getColumnName())
                    .append(" ")
                    .append(sqlType(pm.getType()));
        }

        // TODO: klucze obce dla relacji OneToMany / ManyToOne jeśli chcesz

        sb.append(");");
        return sb.toString();
    }

    /**
     * Prosty przykład tworzenia tabeli pośredniej dla ManyToMany.
     * Tu załóżmy, że w AssociationMetadata masz nazwę tabeli joinTable.
     */
    private String createJoinTableSql(EntityMetadata owner,
                                      AssociationMetadata assoc) {
        String joinTable = assoc.getJoinTable();
        // bardzo uproszczone – w realnym kodzie wziąłbyś typy kluczy itp.
        String ownerFk = owner.getTableName() + "_id";
        String targetFk = assoc.getTargetEntity().getSimpleName().toLowerCase() + "_id";

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ")
                .append(joinTable)
                .append(" (")
                .append(ownerFk).append(" BIGINT NOT NULL, ")
                .append(targetFk).append(" BIGINT NOT NULL")
                // można dodać PK złożony, klucze obce itd.
                .append(");");

        return sb.toString();
    }

    private String sqlType(Class<?> type) {
        if (type == String.class) return "VARCHAR(255)";
        if (type == int.class || type == Integer.class) return "INT";
        if (type == long.class || type == Long.class) return "BIGINT";
        if (type == boolean.class || type == Boolean.class) return "BOOLEAN";
        // itd. – według potrzeb
        return "VARCHAR(255)";
    }

    private String sqlTypeForId(Class<?> type) {
        if (type == Long.class || type == long.class) return "BIGSERIAL";
        // inne typy według potrzeb
        return sqlType(type); // fallback
    }
}
