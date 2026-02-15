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
public class InheritanceMetadata {

    private InheritanceType type;
    private String discriminatorColumnName; // tylko dla SINGLE_TABLE (opcjonalnie JOINED)

    // for SINGLE_TABLE
    private Map<Class<?>, String> classToDiscriminator;
    private Map<String, Class<?>> discriminatorToClass;

    private List<Class<?>> subclasses = new ArrayList<>();

    // Inheritance tree
    private EntityMetadata rootClass;
    private EntityMetadata parent;
    private List<EntityMetadata> children = new ArrayList<>();

    public boolean isRoot() { return parent == null; }

    @Override
    public String toString() {
        if (parent != null) {
            return type.toString() +  " root: " + rootClass.getTableName() + " parent: " + parent.getTableName() + " children: " + children.size();
        }
        else{
            return type.toString() + " root: " + rootClass.getTableName();
        }
    }

    public List<EntityMetadata> getAllChildren() {
        List<EntityMetadata> result = new ArrayList<>();
        Deque<EntityMetadata> stack = new ArrayDeque<>(children);
        while (!stack.isEmpty()) {
            EntityMetadata meta = stack.poll();
            result.add(meta);
            stack.addAll(meta.getInheritanceMetadata().getChildren());
        }
        return result;
    }
}
