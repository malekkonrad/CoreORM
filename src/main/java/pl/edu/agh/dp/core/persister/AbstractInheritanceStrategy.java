package pl.edu.agh.dp.core.persister;

import lombok.NoArgsConstructor;
import pl.edu.agh.dp.core.jdbc.Dialect;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

@NoArgsConstructor
public abstract class AbstractInheritanceStrategy implements InheritanceStrategy {

//    protected final JdbcExecutor jdbcExecutor;
//    protected final Dialect dialect;
//    protected final MetadataRegistry metadataRegistry;
//
//    protected AbstractInheritanceStrategy(JdbcExecutor jdbcExecutor,
//                                          Dialect dialect,
//                                          MetadataRegistry metadataRegistry) {
//        this.jdbcExecutor = jdbcExecutor;
//        this.dialect = dialect;
//        this.metadataRegistry = metadataRegistry;
//    }
//
//    protected String buildInsertReturning(String tableName,
//                                          List<String> columns,
//                                          String idColumnName) {
//        String columnList = String.join(", ", columns);
//        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
//        return "INSERT INTO " + tableName +
//                " (" + columnList + ") VALUES (" + placeholders + ") RETURNING " + idColumnName;
//    }
//
//    protected String buildUpdate(String tableName,
//                                 List<String> columns,
//                                 String idColumnName) {
//        String setClause = String.join(", ",
//                columns.stream().map(c -> c + " = ?").toList());
//        return "UPDATE " + tableName +
//                " SET " + setClause +
//                " WHERE " + idColumnName + " = ?";
//    }
//
//    protected String buildDelete(String tableName, String idColumnName) {
//        return "DELETE FROM " + tableName + " WHERE " + idColumnName + " = ?";
//    }
//
//    /**
//     * Z ResultSet robi prostą mapę nazwa_kolumny -> wartość.
//     */
//    protected Map<String, Object> readRow(ResultSet rs) throws SQLException {
//        Map<String, Object> row = new HashMap<>();
//        ResultSetMetaData meta = rs.getMetaData();
//        int cols = meta.getColumnCount();
//        for (int i = 1; i <= cols; i++) {
//            String name = meta.getColumnLabel(i);
//            Object value = rs.getObject(i);
//            row.put(name, value);
//        }
//        return row;
//    }


}