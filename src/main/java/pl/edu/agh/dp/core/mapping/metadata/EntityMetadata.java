package pl.edu.agh.dp.core.mapping.metadata;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EntityMetadata {
    Class<?> entityClass;
    String tableName;
    PropertyMetadata idProperty;
    List<PropertyMetadata> properties;
    List<AssociationMetadata> associationMetadata;
    InheritanceMetadata inheritanceMetadata;

    public void addProperty(PropertyMetadata pm) {

    }
}
