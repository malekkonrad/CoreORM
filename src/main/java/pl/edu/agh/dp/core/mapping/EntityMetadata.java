package pl.edu.agh.dp.core.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EntityMetadata {
    Class<?> entityClass;
    String tableName;
    List<PropertyMetadata> idColumns = new ArrayList<>();
    List<PropertyMetadata> properties = new ArrayList<>();
    List<PropertyMetadata> fkColumns = new ArrayList<>();
    List<AssociationMetadata> associationMetadata =  new ArrayList<>(); // change to hashset?


    // inheritance
    Class<?> rootMetadata;
    InheritanceMetadata inheritanceMetadata;    // only root classes will have

    public boolean isRoot(){
        return rootMetadata == null;
    }

    public void addProperty(PropertyMetadata pm) {
        properties.add(pm);
        if (pm.isId) idColumns.add(pm);
    }

    public void addAssociationMetadata(AssociationMetadata am) {
        associationMetadata.add(am);
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
        List<PropertyMetadata> cols = new ArrayList<>();

        // FIXME - not sure it's right
        cols.addAll(root.getProperties());

        // traverse through tree
        Deque<EntityMetadata> stack = new ArrayDeque<>(root.getInheritanceMetadata().getChildren());
        while (!stack.isEmpty()) {
            EntityMetadata m = stack.pop();
            cols.addAll(m.getProperties());     // FIXME same question - how to get all columns
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
            cols.addAll(chain.pop().getProperties());       // FIXME same earlier
        }

        return cols;
    }



}
