package pl.edu.agh.dp.core.mapping;

import lombok.Getter;

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
            System.out.println(entity);
            entities.put(clazz, entity);
        }
    }

    public EntityMetadata getEntityMetadata(Class<?> clazz) {
        return entities.get(clazz);
    }
}
