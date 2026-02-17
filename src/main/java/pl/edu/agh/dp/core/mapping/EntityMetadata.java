package pl.edu.agh.dp.core.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.util.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EntityMetadata {
    Class<?> entityClass;
    String tableName;
    boolean isAbstract;
    // maps field name to metadata
    Map<String, PropertyMetadata> idColumns = new HashMap<>();
    Map<String, PropertyMetadata> properties = new HashMap<>();
    Map<String, PropertyMetadata> fkColumns = new HashMap<>();
    Map<String, AssociationMetadata> associationMetadata =  new HashMap<>();
    // inheritance
    Class<?> rootMetadata;
    InheritanceMetadata inheritanceMetadata;

    public void addProperty(PropertyMetadata pm) {
        properties.put(pm.getName(), pm);
        if (pm.isId) idColumns.put(pm.getName(), pm);
    }

    public void addFkProperty(PropertyMetadata pm) {
        fkColumns.put(pm.getName(), pm);
    }

    public void addFkPropertyAll(Map<String, PropertyMetadata> pms) {fkColumns.putAll(pms);}

    public void addIdProperty(PropertyMetadata pm) {
        idColumns.put(pm.getName(), pm);
    }

    public void addIdPropertyAll(Map<String, PropertyMetadata> pms) {idColumns.putAll(pms);}

    public void addAssociationMetadata(AssociationMetadata am) {
        associationMetadata.put(am.getField(), am);
    }

    public TargetStatement getSelectByIdStatement(Object entity) {
        List<String> conditions = new ArrayList<>();
        for (PropertyMetadata pm : idColumns.values()) {
            StringBuilder cond = new StringBuilder();
            cond.append(TargetStatement.getTargetName()).append(".").append(pm.getColumnName());
            cond.append(" = ");

            if (pm.getType() == String.class) cond.append("'");
            cond.append(ReflectionUtils.getFieldValue(entity, pm.getName()));
            if (pm.getType() == String.class) cond.append("'");

            conditions.add(cond.toString());
        }
        return new TargetStatement(String.join(" AND ", conditions), tableName);
    }

    public String getSqlTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(tableName);
        sb.append(" (\n");
        for (PropertyMetadata pm : properties.values()) {
            sb.append(pm.toSqlColumn()).append(",\n");
        }
        for (PropertyMetadata pm : fkColumns.values()) {
            sb.append(pm.toSqlColumn()).append(",\n");
        }
        sb.append(getSqlPrimaryKey());
        sb.append("\n);\n");
        // create association table
        for (AssociationMetadata am : associationMetadata.values()) {
            if (am.getHasForeignKey() && am.getAssociationTable() != null) {
                sb.append(am.getAssociationTable().getSqlTable());
            }
        }
        sb.append("\n");
        // create indexes
        for (PropertyMetadata pm : properties.values()) {
            if (pm.isIndex()) {
                sb.append("CREATE INDEX ");
                sb.append("idx_").append(tableName).append("_").append(pm.getColumnName());
                sb.append(" ON ");
                sb.append(tableName).append("(").append(pm.getColumnName()).append(");\n");
            }
        }
        return sb.toString();
    }

    public String getSqlConstraints() {
        StringBuilder sb = new StringBuilder();
        for (PropertyMetadata pm : fkColumns.values()) {
            sb.append("ALTER TABLE ").append(tableName).append(" ADD ").append(pm.toSqlConstraint(tableName)).append(";\n");
        }
        // create association constraints
        for (AssociationMetadata am : associationMetadata.values()) {
            if (am.getHasForeignKey() && am.getAssociationTable() != null) {
                sb.append(am.getAssociationTable().getSqlConstraintsForAssociationTable());
            }
        }

        return sb.toString();
    }

    public String getSqlConstraintsForAssociationTable() {
        StringBuilder sb = new StringBuilder();

        Map<String, List<PropertyMetadata>> refToColumns = new HashMap<>();

        for (PropertyMetadata pm : fkColumns.values()) {
            if (!refToColumns.containsKey(pm.getReferencedTable())) {
                refToColumns.put(pm.getReferencedTable(), new ArrayList<>());
            }
            refToColumns.get(pm.getReferencedTable()).add(pm);
        }

        for (String referencedTableName : refToColumns.keySet()) {
            List<PropertyMetadata> columns = refToColumns.get(referencedTableName);

            List<String> columnNames = columns.stream().map(PropertyMetadata::getColumnName).toList();
            List<String> references = columns.stream().map(PropertyMetadata::getReferencedName).toList();

            sb.append("ALTER TABLE ")
                .append(tableName)
                .append(" ADD CONSTRAINT ")
                    .append(tableName).append("_")
                    .append(String.join("_", columnNames))
                    .append("_constraint FOREIGN KEY (")
                    .append(String.join(", ", columnNames))
                    .append(")\n").append("REFERENCES ")
                    .append(referencedTableName).append("(")
                    .append(String.join(", ", references))
                    .append(")")
                .append(";\n");
        }
        return sb.toString();
    }

    public String getSqlPrimaryKey() {
        StringBuilder sb = new StringBuilder();
        sb.append("PRIMARY KEY (");
        for (PropertyMetadata pm : idColumns.values()) {
            sb.append(pm.getColumnName()).append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        sb.append(")");
        return sb.toString();
    }

    public List<PropertyMetadata> getNonForeignKeyColumns() {
        List<PropertyMetadata> result = new ArrayList<>();
        EntityMetadata root = inheritanceMetadata.getRootClass(); // we only care about root ids
        for (PropertyMetadata pm : root.idColumns.values()) {
            if (pm.references == null || pm.references.isEmpty()) {
                result.add(pm);
            }
        }
        return result;
    }

    public Map<String, PropertyMetadata> getAllColumnsForSingleTable() {
        EntityMetadata root = getInheritanceMetadata().getRootClass();

        //Get columns from ROOT (id and regular)
        Map<String, PropertyMetadata> columns = new HashMap<>(root.getProperties());

        // Go through children tree
        Deque<EntityMetadata> stack = new ArrayDeque<>(root.getInheritanceMetadata().getChildren());
        while (!stack.isEmpty()) {
            EntityMetadata m = stack.pop();

            // set subclass field nullable
            for (String field : m.getProperties().keySet()) {
                if (!root.getProperties().containsKey(field)) {
                    m.getProperties().get(field).setNullable(true);
                }
            }
            // set correct table name
            m.setTableName(root.getTableName());

            columns.putAll(m.getProperties());
            stack.addAll(m.getInheritanceMetadata().getChildren());
        }

        return columns;
    }

    public Map<String, PropertyMetadata> getFkColumnsForJoinedTable() {
        Map<String, PropertyMetadata> result = new HashMap<>();
        if (inheritanceMetadata.getRootClass() != null
                && !inheritanceMetadata.isRoot()
                && inheritanceMetadata.getParent() != null)
        {
            EntityMetadata root = inheritanceMetadata.getRootClass();
            EntityMetadata parent = inheritanceMetadata.getParent();

            for (PropertyMetadata pm : root.getIdColumns().values()) {
                PropertyMetadata clone = pm.clone();
                clone.setReferences(parent.tableName + "(" + pm.getColumnName() + ")");
                result.put(clone.getName(), clone);
            }
        }
        return result;
    }

    public void setMetadataForConcreteTable() {
        Deque<EntityMetadata> chain = new ArrayDeque<>();
        EntityMetadata cur = this;
        while (cur != null) {
            chain.push(cur);
            cur = cur.getInheritanceMetadata().getParent();
        }

        while (!chain.isEmpty()) {
            EntityMetadata m = chain.pop();
            idColumns.putAll(m.getIdColumns());
            properties.putAll(m.getProperties());
            // NOTE: associationMetadata is NOT copied here — it is deferred
            // to correctRelationships* which runs AFTER fillAssociationData.
        }
    }

    public void setMetadataForSingleTable() {
        EntityMetadata root = getInheritanceMetadata().getRootClass();
        tableName = root.tableName;

        Deque<EntityMetadata> stack = new ArrayDeque<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            EntityMetadata m = stack.pop();

            // set subclass field nullable
            for (String field : m.getProperties().keySet()) {
                if (!root.getProperties().containsKey(field)) {
                    m.getProperties().get(field).setNullable(true);
                }
            }

            idColumns.putAll(m.getIdColumns());
            properties.putAll(m.getProperties());

            stack.addAll(m.getInheritanceMetadata().getChildren());
        }

        // NOTE: associationMetadata is NOT copied here — it is deferred
        // to correctRelationships* which runs AFTER fillAssociationData.
    }

    public void correctRelationshipsConcreteClass() {
        Deque<EntityMetadata> chain = new ArrayDeque<>();
        EntityMetadata cur = this;
        while (cur != null) {
            chain.push(cur);
            cur = cur.getInheritanceMetadata().getParent();
        }

        while (!chain.isEmpty()) {
            EntityMetadata m = chain.pop();
            if (m.isAbstract()) {
                fkColumns.putAll(m.getFkColumns());
                for (String name : m.getAssociationMetadata().keySet()) {
                    AssociationMetadata assoc = m.getAssociationMetadata().get(name);
                    AssociationMetadata newAm = new AssociationMetadata(assoc);
                    newAm.setTableName(tableName);
                    associationMetadata.put(name, newAm);
                }
            } else {
                associationMetadata.putAll(m.getAssociationMetadata());
            }
        }
    }

    public void correctRelationshipsJoined() {
        Deque<EntityMetadata> chain = new ArrayDeque<>();
        EntityMetadata cur = this;
        while (cur != null) {
            chain.push(cur);
            cur = cur.getInheritanceMetadata().getParent();
        }

        while (!chain.isEmpty()) {
            EntityMetadata m = chain.pop();
            associationMetadata.putAll(m.getAssociationMetadata());
        }
    }

    public void correctRelationshipsSingle() {
        EntityMetadata root = getInheritanceMetadata().getRootClass();

        Deque<EntityMetadata> stack = new ArrayDeque<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            EntityMetadata m = stack.pop();
            fkColumns.putAll(m.getFkColumns());
            associationMetadata.putAll(m.getAssociationMetadata());

            stack.addAll(m.getInheritanceMetadata().getChildren());
        }

        correctRelationshipsTableNames();
    }

    public void correctRelationshipsConcrete() {
        Deque<EntityMetadata> chain = new ArrayDeque<>();
        EntityMetadata cur = this;
        while (cur != null) {
            chain.push(cur);
            cur = cur.getInheritanceMetadata().getParent();
        }

        while (!chain.isEmpty()) {
            EntityMetadata m = chain.pop();
            fkColumns.putAll(m.getFkColumns());
            associationMetadata.putAll(m.getAssociationMetadata());
        }

        correctRelationshipsTableNames();
    }

    /**
     * For CONCRETE_CLASS strategy: merge properties from abstract ancestors,
     * but treat non-abstract ancestors as joined (FK reference).
     *
     * Only merge from abstract ancestors that are DIRECT parents before
     * the first concrete ancestor. Once we hit a concrete parent, stop —
     * that parent already has all abstract fields above it merged.
     */
    public void setMetadataForConcreteClass() {
        // Walk upward from this, merging only abstract ancestors
        // until we reach the first concrete (non-abstract) ancestor.
        EntityMetadata cur = inheritanceMetadata.getParent();
        while (cur != null) {
            if (!cur.isAbstract()) {
                // Hit a concrete parent — stop merging.
                // This parent already has all abstract fields above it merged.
                break;
            }
            // Abstract parent → merge its fields (like TPC)
            idColumns.putAll(cur.getIdColumns());
            properties.putAll(cur.getProperties());
            // NOTE: associationMetadata is NOT copied here — it is deferred
            // to correctRelationships* which runs AFTER fillAssociationData.
            cur = cur.getInheritanceMetadata().getParent();
        }
    }

    /**
     * For CONCRETE_CLASS strategy: create FK columns referencing the nearest
     * non-abstract parent (not the root, as in joined).
     */
    public Map<String, PropertyMetadata> getFkColumnsForConcreteClass() {
        Map<String, PropertyMetadata> result = new HashMap<>();

        // Find nearest non-abstract parent
        EntityMetadata nearestConcreteParent = findNearestConcreteParent();
        if (nearestConcreteParent == null) {
            return result; // no concrete parent, nothing to join
        }

        // Use root's ID columns as the FK columns, referencing the nearest concrete
        // parent's table
        EntityMetadata root = inheritanceMetadata.getRootClass();
        for (PropertyMetadata pm : root.getIdColumns().values()) {
            PropertyMetadata clone = pm.clone();
            clone.setReferences(nearestConcreteParent.tableName + "(" + pm.getColumnName() + ")");
            result.put(clone.getName(), clone);
        }
        return result;
    }

    /**
     * Find the nearest non-abstract parent in the hierarchy.
     */
    public EntityMetadata findNearestConcreteParent() {
        EntityMetadata cur = inheritanceMetadata.getParent();
        while (cur != null) {
            if (!cur.isAbstract()) {
                return cur;
            }
            cur = cur.getInheritanceMetadata().getParent();
        }
        return null;
    }

    public void correctRelationshipsTableNames() {
        Map<String, AssociationMetadata> result = new HashMap<>();
        if (associationMetadata != null) {
            associationMetadata.forEach(
                    (key, value) -> {
                        AssociationMetadata am = new AssociationMetadata(value);
                        am.setTableName(tableName);
                        result.put(key, am);
                    });
            associationMetadata = result;
        }
    }

    public List<PropertyMetadata> getColumnsForConcreteTable() {
        List<PropertyMetadata> ids = new ArrayList<>();
        List<PropertyMetadata> others = new ArrayList<>();

        // 1. Zbuduj łańcuch od najwyższego rodzica do obecnej klasy
        Deque<EntityMetadata> chain = new ArrayDeque<>();
        EntityMetadata cur = this;
        while (cur != null) {
            chain.push(cur); // push wrzuca na stos, więc rodzic będzie na wierzchu przy zdejmowaniu
            cur = cur.getInheritanceMetadata().getParent();
        }

        // 2. Iteruj od rodzica w dół
        while (!chain.isEmpty()) {
            EntityMetadata m = chain.pop();
            collectColumnsFromMetadata(m, ids, others);
        }

        // 3. Złącz: ID + reszta
        ids.addAll(others);
        return ids;
    }

    private void collectColumnsFromMetadata(EntityMetadata m, List<PropertyMetadata> ids,
            List<PropertyMetadata> others) {
        // Dodaj ID (zakładając, że idColumns zawiera tylko ID)
        ids.addAll(m.getIdColumns().values());

        // Dodaj pozostałe właściwości, filtrując ID, bo `properties` zawiera wszystko
        for (PropertyMetadata pm : m.getProperties().values()) {
            if (!pm.isId) { // Ważne: sprawdzamy flagę isId (zakładam, że masz takie pole w
                            // PropertyMetadata)
                others.add(pm);
            }
            // Alternatywnie, jeśli nie masz pola isId w PropertyMetadata, sprawdź czy klucz
            // istnieje w idColumns:
            // if (!m.getIdColumns().containsKey(pm.getName())) {
            // others.add(pm);
            // }
        }
    }

    // for testing purposes
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EntityMetadata{\n");
        sb.append("  entityClass: ").append(entityClass != null ? entityClass.getName() : "null").append("\n");
        sb.append("  tableName: ").append(tableName).append("\n");
        sb.append("  idColumns: ").append(idColumns).append("\n");
        sb.append("  properties: ").append(properties).append("\n");
        sb.append("  fkColumns: ").append(fkColumns).append("\n");
        sb.append("  associations: ").append(associationMetadata).append("\n");
        sb.append("  inheritanceMetadata: ").append(inheritanceMetadata).append("\n");
        sb.append("}");
        return sb.toString();
    }
}
