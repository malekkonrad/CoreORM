package pl.edu.agh.dp.core.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    // inheritance
    Class<?> rootMetadata;
    InheritanceMetadata inheritanceMetadata;

    public void addProperty(PropertyMetadata pm) {
        properties.put(pm.getName(), pm);
        if (pm.isId) idColumns.put(pm.getName(), pm);
    }

    public void addAssociationMetadata(AssociationMetadata am) {
        associationMetadata.put(am.getField(), am);
    }

    // for testing purposes
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EntityMetadata{\n");
        sb.append("  entityClass: ").append(entityClass.getName()).append("\n");
        sb.append("  tableName: ").append(tableName).append("\n");
        sb.append("  idColumns: ").append(idColumns).append("\n");
        sb.append("  properties: ").append(properties).append("\n");
        sb.append("  associations: ").append(associationMetadata).append("\n");
        sb.append("  inheritanceMetadata: ").append(inheritanceMetadata).append("\n");
        sb.append("}");
        return sb.toString();
    }


    public List<PropertyMetadata> getColumnsForSingleTable() {
        EntityMetadata root = getInheritanceMetadata().getRootClass();

        // FIXME - not sure it's right
        List<PropertyMetadata> cols = new ArrayList<>(root.getProperties().values());

        // traverse through tree
        Deque<EntityMetadata> stack = new ArrayDeque<>(root.getInheritanceMetadata().getChildren());
        while (!stack.isEmpty()) {
            EntityMetadata m = stack.pop();
            cols.addAll(m.getProperties().values());     // FIXME same question - how to get all columns
            stack.addAll(m.getInheritanceMetadata().getChildren());
        }
        return cols;
    }

    public List<PropertyMetadata> getColumnsForConcreteTable() {
        List<PropertyMetadata> cols = new ArrayList<>();

        // id + pola z całego łańcucha rodziców
        Deque<EntityMetadata> chain = new ArrayDeque<>();
        EntityMetadata cur = this;
        while (cur != null) {
            chain.push(cur);
            cur = cur.getInheritanceMetadata().getParent();
        }

        while (!chain.isEmpty()) {
            cols.addAll(chain.pop().getProperties().values());       // FIXME same earlier
        }

        return cols;
    }



}

