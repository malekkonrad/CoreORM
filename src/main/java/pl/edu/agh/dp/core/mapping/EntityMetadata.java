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
    // maps field name to metadata
    Map<String, PropertyMetadata> idColumns = new HashMap<>();
    Map<String, PropertyMetadata> properties = new HashMap<>();
    Map<String, PropertyMetadata> fkColumns = new HashMap<>();
    Map<String, AssociationMetadata> associationMetadata =  new HashMap<>();
    // TODO add DType

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

    public void addIdProperty(PropertyMetadata pm) {
        idColumns.put(pm.getName(), pm);
    }

    public void addAssociationMetadata(AssociationMetadata am) {
        associationMetadata.put(am.getField(), am);
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

    public String getSelectByIdStatement(Object entity) {
        List<String> conditions = new ArrayList<>();
        for (PropertyMetadata pm : idColumns.values()) {
            StringBuilder cond = new StringBuilder();
            cond.append(tableName).append(".").append(pm.getColumnName());
            cond.append(" = ");
            cond.append(ReflectionUtils.getFieldValue(entity, pm.getName()));
            conditions.add(cond.toString());
        }
        return String.join(" AND ", conditions);
    }

    public String getSqlTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(tableName);
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
        return sb.toString();
    }

    public String getSqlConstraints() {
        StringBuilder sb = new StringBuilder();
        for (PropertyMetadata pm : fkColumns.values()) {
            sb.append("ALTER TABLE ").append(tableName).append(" ADD ").append(pm.toSqlConstraint()).append(";\n");
        }
        // create association constraints
        for (AssociationMetadata am : associationMetadata.values()) {
            if (am.getHasForeignKey() && am.getAssociationTable() != null) {
                sb.append(am.getAssociationTable().getSqlConstraints());
            }
        }
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
        for (PropertyMetadata pm : idColumns.values()) {
            if (pm.references == null || pm.references.isEmpty()) {
                result.add(pm);
            }
        }
        return result;
    }

    public Map<String, PropertyMetadata> getAllColumnsForSingleTable() {

        EntityMetadata root = getInheritanceMetadata().getRootClass();

        // 1. Pobierz kolumny z ROOT (id i zwykłe)

//        collectColumnsFromMetadata(root, ids, columns);
        Map<String, PropertyMetadata> columns = new HashMap<>(root.getProperties());

        // 2. Przejdź przez drzewo dzieci
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
            // TODO warn about different table name
            m.setTableName(root.getTableName());

            columns.putAll(m.getProperties());

            stack.addAll(m.getInheritanceMetadata().getChildren());
        }

        return columns;
    }

    public Map<String, PropertyMetadata> getIdColumnsForSingleTable() {

        EntityMetadata root = getInheritanceMetadata().getRootClass();

        // 1. Pobierz kolumny z ROOT (id i zwykłe)

//        collectColumnsFromMetadata(root, ids, columns);
        Map<String, PropertyMetadata> idColumns = new HashMap<>(root.getIdColumns());

        // 2. Przejdź przez drzewo dzieci
        Deque<EntityMetadata> stack = new ArrayDeque<>(root.getInheritanceMetadata().getChildren());
        while (!stack.isEmpty()) {
            EntityMetadata m = stack.pop();

            // Pobieramy kolumny z dziecka
//            collectColumnsFromMetadata(m, ids, columns);
            // TODO children should not have any more ids
            idColumns.putAll(m.getIdColumns());

            stack.addAll(m.getInheritanceMetadata().getChildren());
        }

        return idColumns;
    }

    public Map<String, PropertyMetadata> getIdColumnsForConcreteTable() {
        Map<String, PropertyMetadata> columns = new HashMap<>();
        // 1. Zbuduj łańcuch od najwyższego rodzica do obecnej klasy
        Deque<EntityMetadata> chain = new ArrayDeque<>();
        EntityMetadata cur = this;
        while (cur != null) {
            chain.push(cur); // push wrzuca na stos, więc rodzic będzie na wierzchu przy zdejmowaniu
            cur = cur.getInheritanceMetadata().getParent();
        }

        while (!chain.isEmpty()) {
            EntityMetadata m = chain.pop();
            columns.putAll(m.getIdColumns());
        }

        return columns;
    }

    public Map<String, PropertyMetadata> getAllColumnsForConcreteTable() {
        Map<String, PropertyMetadata> columns = new HashMap<>();
        // 1. Zbuduj łańcuch od najwyższego rodzica do obecnej klasy
        Deque<EntityMetadata> chain = new ArrayDeque<>();
        EntityMetadata cur = this;
        while (cur != null) {
            chain.push(cur); // push wrzuca na stos, więc rodzic będzie na wierzchu przy zdejmowaniu
            cur = cur.getInheritanceMetadata().getParent();
        }

        while (!chain.isEmpty()) {
            EntityMetadata m = chain.pop();
            columns.putAll(m.getProperties());
        }

        return columns;
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

    private void collectColumnsFromMetadata(EntityMetadata m, List<PropertyMetadata> ids, List<PropertyMetadata> others) {
        // Dodaj ID (zakładając, że idColumns zawiera tylko ID)
        ids.addAll(m.getIdColumns().values());

        // Dodaj pozostałe właściwości, filtrując ID, bo `properties` zawiera wszystko
        for (PropertyMetadata pm : m.getProperties().values()) {
            if (!pm.isId) { // Ważne: sprawdzamy flagę isId (zakładam, że masz takie pole w PropertyMetadata)
                others.add(pm);
            }
            // Alternatywnie, jeśli nie masz pola isId w PropertyMetadata, sprawdź czy klucz istnieje w idColumns:
            // if (!m.getIdColumns().containsKey(pm.getName())) {
            //    others.add(pm);
            // }
        }
    }



}

