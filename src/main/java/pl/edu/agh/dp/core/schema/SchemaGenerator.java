package pl.edu.agh.dp.core.schema;


import pl.edu.agh.dp.core.jdbc.ConnectionProvider;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;
import pl.edu.agh.dp.core.mapping.AssociationMetadata;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;

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

        StringBuilder primary_keys = new StringBuilder();
        for (PropertyMetadata pm : meta.getProperties()) {
            sb.append(pm.getColumnName()).append(" ")
                    .append(pm.getSqlType()).append(" ");
            if (pm.isNullable()) sb.append("NULL ");
            else sb.append("NOT NULL ");
            if (pm.isUnique()) sb.append("UNIQUE ");
            if (pm.getDefaultValue() != null) sb.append("DEFAULT ").append(pm.getDefaultValue().toString()); // TODO check if works correctly
            sb.append(", ");
            // append to primary keys separately
            if (pm.isId()) primary_keys.append(pm.getColumnName()).append(", ");
        }
        sb.append("PRIMARY KEY (").append(primary_keys);
        sb.delete(sb.length() - 2, sb.length()); // delete last ", " from primary keys
        sb.append(")");

        // TODO: set index on table
        // TODO: klucze obce dla relacji OneToMany / ManyToOne jeśli chcesz

        sb.append(");");
//        System.out.println(sb);
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
}
