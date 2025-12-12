package pl.edu.agh.dp.core.persister;

import lombok.NoArgsConstructor;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class EntityPersisterImpl implements EntityPersister {

    private EntityMetadata metadata;

    public EntityPersisterImpl(EntityMetadata metadata) {
        this.metadata = metadata;
    }


    @Override
    public Object findById(Object id, Session session) {
        return null;
    }

    @Override
    public void insert(Object entity, Session session) {
        try {
//            SessionImpl sess = (SessionImpl) session;
            Connection con = session.getConnection();

            PropertyMetadata idMeta = metadata.getIdProperty();
            Object idValue = ReflectionUtils.getFieldValue(entity, idMeta.getName());
            boolean idProvided = idValue != null;

            List<String> columns = new ArrayList<>();
            List<Object> values = new ArrayList<>();

            // ID tylko jeśli nie jest null
            if (idProvided) {
                columns.add(idMeta.getColumnName());
                values.add(idValue);
            }

            // Zwykłe kolumny
            for (PropertyMetadata pm : metadata.getProperties()) {
                columns.add(pm.getColumnName());
                Object value = ReflectionUtils.getFieldValue(entity, pm.getName());
                values.add(value);
            }

            StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO ")
                    .append(metadata.getTableName())
                    .append(" (")
                    .append(String.join(", ", columns))
                    .append(") VALUES (")
                    .append("?,".repeat(values.size()));
            sql.deleteCharAt(sql.length() - 1);
            sql.append(")");

            // Chcemy dostać wygenerowane klucze
            try (PreparedStatement ps = con.prepareStatement(sql.toString(),
                    Statement.RETURN_GENERATED_KEYS)) {

                for (int i = 0; i < values.size(); i++) {
                    ps.setObject(i + 1, values.get(i));
                }

                ps.executeUpdate();

                // Jeżeli ID nie było ustawione – odczytaj je z DB i wpisz do obiektu
                if (!idProvided) {
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            Object generatedId = keys.getObject(1);
                            ReflectionUtils.setFieldValue(entity, idMeta.getName(), generatedId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error inserting entity " + entity, e);
        }
    }

    @Override
    public void update(Object entity, Session session) {

    }

    @Override
    public void delete(Object entity, Session session) {

    }



//    private String createTableSql(EntityMetadata meta) {
//        StringBuilder sb = new StringBuilder();
//        sb.append("CREATE TABLE IF NOT EXISTS ")
//                .append(meta.getTableName())
//                .append(" (");
//
//        // kolumna ID
//        PropertyMetadata id = meta.getIdProperty();
//        sb.append(id.getColumnName())
//                .append(" ")
//                .append(sqlType(id.getType()))
//                .append(" PRIMARY KEY");
//
//        // zwykłe kolumny
//        for (PropertyMetadata pm : meta.getProperties()) {
//            sb.append(", ")
//                    .append(pm.getColumnName())
//                    .append(" ")
//                    .append(sqlType(pm.getType()));
//        }
//
//        // TODO: klucze obce dla relacji OneToMany / ManyToOne jeśli chcesz
//
//        sb.append(");");
//        return sb.toString();
//    }
}
