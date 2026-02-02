package pl.edu.agh.dp.core.schema;

import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.jdbc.ConnectionProvider;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class SchemaValidator {

    private final MetadataRegistry registry;
    private final ConnectionProvider connectionProvider;

    public SchemaValidator(MetadataRegistry registry, ConnectionProvider connectionProvider) {
        this.registry = registry;
        this.connectionProvider = connectionProvider;
    }

    /**
     * Validates that the database schema matches the entity metadata.
     * Throws IntegrityException if schema is invalid.
     */
    public void validate() {
        try (Connection conn = connectionProvider.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            for (EntityMetadata entityMeta : registry.getEntities().values()) {
                String tableName = entityMeta.getTableName();

                // Check if table exists
                if (!tableExists(metaData, tableName)) {
                    throw new IntegrityException("Table " + tableName + " does not exist in database");
                }

                // Check if all required columns exist
                Set<String> existingColumns = getTableColumns(metaData, tableName);
                for (PropertyMetadata prop : entityMeta.getProperties().values()) {
                    String columnName = prop.getColumnName();
                    if (!existingColumns.contains(columnName.toLowerCase())) {
                        throw new IntegrityException("Column " + columnName + " does not exist in table " + tableName);
                    }
                }
            }

            System.out.println("Schema validation successful - all tables and columns exist");
        } catch (SQLException e) {
            throw new IntegrityException("Error validating schema: " + e.getMessage());
        }
    }

    /**
     * Checks if a table exists in the database
     */
    public boolean tableExists(DatabaseMetaData metaData, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }
        // Try uppercase (some databases store table names in uppercase)
        try (ResultSet rs = metaData.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }
        // Try lowercase
        try (ResultSet rs = metaData.getTables(null, null, tableName.toLowerCase(), new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /**
     * Gets all column names for a table
     */
    private Set<String> getTableColumns(DatabaseMetaData metaData, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        
        // Try as-is
        try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }
        
        // If no columns found, try uppercase
        if (columns.isEmpty()) {
            try (ResultSet rs = metaData.getColumns(null, null, tableName.toUpperCase(), null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }
        
        // If still no columns, try lowercase
        if (columns.isEmpty()) {
            try (ResultSet rs = metaData.getColumns(null, null, tableName.toLowerCase(), null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }
        
        return columns;
    }
}
