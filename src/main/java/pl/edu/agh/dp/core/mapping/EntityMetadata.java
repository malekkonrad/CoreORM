package pl.edu.agh.dp.core.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
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
    List<AssociationMetadata> associationMetadata =  new ArrayList<>();
    InheritanceMetadata inheritanceMetadata;

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
        sb.append("}");
        return sb.toString();
    }
}
