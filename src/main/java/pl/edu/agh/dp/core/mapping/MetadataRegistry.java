package pl.edu.agh.dp.core.mapping;

import lombok.Getter;
import pl.edu.agh.dp.core.mapping.metadata.EntityMetadata;

import java.util.HashMap;
import java.util.Map;

@Getter
public class MetadataRegistry {
    Map<Class<?>, EntityMetadata> entities;


    public void build(EntityMetadata meta){
        entities = new HashMap<>();
    }
}
