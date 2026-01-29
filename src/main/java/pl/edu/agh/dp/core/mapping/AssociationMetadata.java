package pl.edu.agh.dp.core.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.dp.core.exceptions.IntegrityException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@AllArgsConstructor
public class AssociationMetadata {

    public enum Type {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY
    }

    private Type type;
    private Class<?> targetEntity;
    private String field;
    private String mappedBy;
    private Boolean hasForeignKey;
    private String tableName;
    private String targetTableName;
    private List<PropertyMetadata> joinColumns;
    private List<PropertyMetadata> targetJoinColumns;
    private EntityMetadata associationTable;

    public PropertyMetadata getFieldProperty() {
        PropertyMetadata fieldMeta = null;
        for (PropertyMetadata pm : joinColumns) {
            if (pm.getName().equals(field)) {
                fieldMeta = pm;
            }
        }
        if (fieldMeta == null) {
            throw new IntegrityException("Field '" + field + "' not found.");
        }
        return fieldMeta;
    }

    public String getJoinStatement() {
        StringBuilder joinStmt = new StringBuilder();
        joinStmt.append("INNER JOIN ");

        if (type == Type.MANY_TO_MANY) {
            String tableName = associationTable.getTableName();


            joinStmt.append(tableName).append(" ON ");
            List<String> conditions = new ArrayList<>();
            for (PropertyMetadata pm : associationTable.getFkColumns().values()) {
                if (!Objects.equals(pm.getReferencedTable(), this.targetTableName)) continue;
                conditions.add(
                        pm.getReferencedTable() + "." + pm.getReferencedName() + " = "
                                + tableName + "." + pm.getColumnName()
                );
            }
            joinStmt.append(String.join(" AND ", conditions));

            joinStmt.append(" INNER JOIN ").append(this.tableName).append(" ON ");
            conditions.clear();
            for (PropertyMetadata pm : associationTable.getFkColumns().values()) {
                if (!Objects.equals(pm.getReferencedTable(), this.tableName)) continue;
                conditions.add(
                        pm.getReferencedTable() + "." + pm.getReferencedName() + " = "
                                + tableName + "." + pm.getColumnName()
                );
            }
            joinStmt.append(String.join(" AND ", conditions));

            return joinStmt.toString();
        }

        // select non-empty joinColumns
        List<PropertyMetadata> columns = joinColumns;
        PropertyMetadata firstPm = columns.get(0);
        String tableName = this.tableName;
        if (firstPm.getColumnName() == null) {
            columns = targetJoinColumns;
            firstPm = columns.get(0);
            tableName = targetTableName;
        }
        if (firstPm.getColumnName() == null) {
            throw new IntegrityException("Invalid join Columns.");
        }
        // append table name
        joinStmt.append(firstPm.getReferencedTable()).append(" ON ");

        List<String> conditions = new ArrayList<>();
        for (PropertyMetadata pm : columns) {
            conditions.add(
                    pm.getReferencedTable() + "." + pm.getReferencedName() + " = "
                    + tableName + "." + pm.getColumnName()
            );
        }
        joinStmt.append(String.join(" AND ", conditions));
        return joinStmt.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AssociationMetadata{\n");
        sb.append("  field: ").append(field).append("\n");
        sb.append("  type: ").append(type).append("\n");
        sb.append("  targetEntity: ").append(targetEntity.getName()).append("\n");
        sb.append("  mappedBy: ").append(mappedBy).append("\n");
        sb.append("  tableName: ").append(tableName).append("\n");
        sb.append("  joinColumns: ").append(joinColumns).append("\n");
        sb.append("  targetJoinColumns: ").append(targetJoinColumns).append("\n");
        sb.append("}");
        return sb.toString();
    }
}
