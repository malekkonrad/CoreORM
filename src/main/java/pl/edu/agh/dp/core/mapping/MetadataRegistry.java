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

        // TODO update associations, with table info because now we have that information
        // Helper code to get argument type from collection
//        Field field = Test1.class.getField("list");
//
//        Type genericFieldType = field.getGenericType();
//
//        if(genericFieldType instanceof ParameterizedType){
//            ParameterizedType aType = (ParameterizedType) genericFieldType;
//            Type[] fieldArgTypes = aType.getActualTypeArguments();
//            for(Type fieldArgType : fieldArgTypes){
//                Class fieldArgClass = (Class) fieldArgType;
//                System.out.println("fieldArgClass = " + fieldArgClass);
//            }
//        }

    }

    public EntityMetadata getEntityMetadata(Class<?> clazz) {
        return entities.get(clazz);
    }
}
