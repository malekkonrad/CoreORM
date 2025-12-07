package pl.edu.agh.dp.core.mapping;

import pl.edu.agh.dp.core.mapping.metadata.EntityMetadata;
import pl.edu.agh.dp.core.mapping.metadata.PropertyMetadata;

import java.lang.reflect.Field;

public class MetadataBuilder {
    public static EntityMetadata buildEntityMetadata(Class<?> clazz) {
        EntityMetadata meta = new EntityMetadata();
        meta.setEntityClass(clazz);

        // TODO: process @Entity, @Table, @Id, @Column
        // TODO: apply naming conventions
        // TODO: detect inheritance
        // TODO: detect associations

        // SIMPLE: basic mapping (for now)
        meta.setTableName(clazz.getSimpleName().toLowerCase());

        for (Field f : clazz.getDeclaredFields()) {
            PropertyMetadata pm =
                    new PropertyMetadata(f.getName(),
                            f.getName(),
                            f.getType(),
                            f.getName().equals("id"));
            if (pm.isId()) meta.setIdProperty(pm);
            else meta.addProperty(pm);
        }

        return meta;
    }
}
