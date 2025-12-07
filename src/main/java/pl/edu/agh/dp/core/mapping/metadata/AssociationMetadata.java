package pl.edu.agh.dp.core.mapping.metadata;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AssociationMetadata {

    public enum Type {
        ONE_TO_MANY,
        MANY_TO_ONE,
        MANY_TO_MANY
    }

    private Type type;
    private Class<?> targetEntity;
    private String mappedBy;
    private String joinTable;
    private String joinColumn;
}
