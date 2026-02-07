package pl.edu.agh.dp.core.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.dp.core.api.Session;
import pl.edu.agh.dp.core.api.LazyList;
import pl.edu.agh.dp.core.api.LazySet;
import pl.edu.agh.dp.core.exceptions.IntegrityException;

import java.util.*;

@Getter
@Setter
@AllArgsConstructor
public class AssociationMetadata {

    public static final String alias = "boss";

    public enum Type {
        ONE_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY
    }

    public enum CollectionType {
        NONE,
        LIST,
        SET
    }

    private Type type;
    private Class<?> targetEntity;
    private String field;
    private String mappedBy;
    private Boolean hasForeignKey;
    private String tableName;
    private String targetTableName;
    private CollectionType collectionType;
    private List<PropertyMetadata> joinColumns;
    private List<PropertyMetadata> targetJoinColumns;
    private EntityMetadata associationTable;

    public AssociationMetadata(AssociationMetadata other) {
        this.type = other.type;
        this.targetEntity = other.targetEntity;
        this.field = other.field;
        this.mappedBy = other.mappedBy;
        this.hasForeignKey = other.hasForeignKey;
        this.tableName = other.tableName;
        this.targetTableName = other.targetTableName;
        this.collectionType = other.collectionType;

        this.joinColumns = other.joinColumns == null
                ? null
                : other.joinColumns.stream()
                .map(PropertyMetadata::new)
                .toList();

        this.targetJoinColumns = other.targetJoinColumns == null
                ? null
                : other.targetJoinColumns.stream()
                .map(PropertyMetadata::new)
                .toList();

        this.associationTable = other.associationTable; // intentional shared reference
    }

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

    public Collection<?> createLazyCollection(Session session, Object owner) {
        if (collectionType == CollectionType.NONE) {
            throw new IntegrityException("In this relationship, collections are not supported.");
        } else if (collectionType == CollectionType.LIST) {
            return new LazyList<>(session, owner, field);
        } else if (collectionType == CollectionType.SET) {
            return new LazySet<>(session, owner, field);
        } else {
            throw new IntegrityException("Collection '" + collectionType + "' is not supported.");
        }
    }

    public Collection<?> createCollection() {
        if (collectionType == CollectionType.NONE) {
            throw new IntegrityException("In this relationship, collections are not supported.");
        } else if (collectionType == CollectionType.LIST) {
            return new ArrayList<>();
        } else if (collectionType == CollectionType.SET) {
            return new HashSet<>();
        } else {
            throw new IntegrityException("Collection '" + collectionType + "' is not supported.");
        }
    }

    public TargetStatement getJoinStatement() {
        StringBuilder joinStmt = new StringBuilder();
        joinStmt.append("INNER JOIN ");

        if (type == Type.MANY_TO_MANY) {
            String associationTableName = associationTable.getTableName();


            joinStmt.append(associationTableName).append(" ON ");
            List<String> conditions = new ArrayList<>();
            for (PropertyMetadata pm : joinColumns) {
                conditions.add(
                        TargetStatement.getTargetName() + "." + pm.getReferencedName() + " = "
                                + associationTableName + "." + pm.getColumnName()
                );
            }
            joinStmt.append(String.join(" AND ", conditions));

            joinStmt.append(" INNER JOIN ").append(tableName).append(" AS ").append(alias).append(" ON ");
            conditions.clear();
            for (PropertyMetadata pm : targetJoinColumns) {
                conditions.add(
                        alias + "." + pm.getReferencedName() + " = "
                                + associationTableName + "." + pm.getColumnName()
                );
            }
            joinStmt.append(String.join(" AND ", conditions));

            return new TargetStatement(joinStmt.toString(), targetTableName, alias);
        }

        // add alias if necessary
//        boolean isAlias = tableName == targetTableName;
//        String alias = isAlias ? AssociationMetadata.alias : tableName;
        // append table name
        joinStmt.append(tableName);
//        if (isAlias)
            joinStmt.append(" AS ").append(alias);
        joinStmt.append(" ON ");

        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < joinColumns.size(); i++) {
            PropertyMetadata pm = joinColumns.get(i);
            PropertyMetadata targetPm = targetJoinColumns.get(i);
            conditions.add(
                    alias + "." + pm.getColumnName() + " = "
                    + TargetStatement.getTargetName() + "." + targetPm.getColumnName()
            );
        }
        joinStmt.append(String.join(" AND ", conditions));
        return new TargetStatement(joinStmt.toString(), targetTableName, alias);
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
        sb.append("  targetTableName: ").append(targetTableName).append("\n");
        sb.append("  joinColumns: ").append(joinColumns).append("\n");
        sb.append("  targetJoinColumns: ").append(targetJoinColumns).append("\n");
        sb.append("  collectionType: ").append(collectionType).append("\n");
        sb.append("  associationTable: ").append(associationTable).append("\n");
        sb.append("}");
        return sb.toString();
    }
}
