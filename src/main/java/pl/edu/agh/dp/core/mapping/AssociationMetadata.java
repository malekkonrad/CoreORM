package pl.edu.agh.dp.core.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

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
    private String joinTable; // TODO maybe change to EntityMetadata or skip
    private List<PropertyMetadata> joinColumns;
    private List<PropertyMetadata> targetJoinColumns;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AssociationMetadata{\n");
        sb.append("  type: ").append(type).append("\n");
        sb.append("  targetEntity: ").append(targetEntity.getName()).append("\n");
        sb.append("  mappedBy: ").append(mappedBy).append("\n");
        sb.append("  joinTable: ").append(joinTable).append("\n");
        sb.append("  joinColumns: ").append(joinColumns).append("\n");
        sb.append("  targetJoinColumns: ").append(targetJoinColumns).append("\n");
        sb.append("}");
        return sb.toString();
    }
}
