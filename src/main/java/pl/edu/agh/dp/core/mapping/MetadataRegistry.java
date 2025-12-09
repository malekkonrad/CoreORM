package pl.edu.agh.dp.core.mapping;

import lombok.Getter;
import pl.edu.agh.dp.core.mapping.metadata.EntityMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class MetadataRegistry {

    private Map<Class<?>, EntityMetadata> entities;

    public void build(List<Class<?>> entitiesClasses) {
        entities = new HashMap<>();

        for (Class<?> clazz : entitiesClasses){
            EntityMetadata entity =  MetadataBuilder.buildEntityMetadata(clazz);

            entities.put(clazz, entity);
        }
    }
}
