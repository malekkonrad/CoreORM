package pl.edu.agh.dp.core.mapping;

import lombok.Getter;
import pl.edu.agh.dp.api.annotations.Entity;
import pl.edu.agh.dp.api.annotations.Inheritance;

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

        // inheritance
//        entities = MetadataBuilder.buildInheritanceMetadataMap(entities);
        shittyBuissness();

        for (Class<?> clazz : entitiesClasses){
            EntityMetadata entity =  entities.get(clazz);
            System.out.println(entity);
        }


        // TODO update associations, with table info because now we have that information
        for (Class<?> clazz : entitiesClasses){
            fillAssociationData(clazz);
        }

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

    private void fillAssociationData(Class<?> clazz) {
        EntityMetadata meta = entities.get(clazz);
    }

    private void shittyBuissness(){



        for (EntityMetadata entity : entities.values()) {
            entity.setInheritanceMetadata(new InheritanceMetadata());
        }

        // FIXME change to one for loop
        for (Class<?> clazz : entities.keySet()) {
            EntityMetadata entity = entities.get(clazz);
            InheritanceType type = getStrategy(clazz);
            entity.getInheritanceMetadata().setType(type);
        }

        for (EntityMetadata meta : entities.values()) {

            Class<?> sup = meta.getEntityClass().getSuperclass();

            while (sup != null && sup != Object.class && !isEntity(sup)) {
                sup = sup.getSuperclass();
            }
            if (sup != null && isEntity(sup)) {
                EntityMetadata parent = entities.get(sup);

                // set parent
                meta.getInheritanceMetadata().setParent(parent);

                // add current as children to parent
                parent.getInheritanceMetadata().getChildren().add(meta);
            }

        }

        // set root
        for (EntityMetadata m : entities.values()) {
            EntityMetadata r = m;
            while (r.getInheritanceMetadata().getParent() != null) r = r.getInheritanceMetadata().getParent();
            m.getInheritanceMetadata().setRootClass(r);
        }
    }

    private static InheritanceType getStrategy(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != Object.class) {
            if (current.isAnnotationPresent(Inheritance.class)) {
                return current.getAnnotation(Inheritance.class).strategy();
            }
            current = current.getSuperclass();
        }
        // Default SINGLE_TABLE
        if (clazz.getSuperclass() != Object.class && clazz.getSuperclass().isAnnotationPresent(Entity.class)) {
            return InheritanceType.SINGLE_TABLE;
        }
        return InheritanceType.SINGLE_TABLE; // FIXME: zastanawiam się co w przypadku braku dziedziczenia - NoInheritance???
    }


    private boolean isEntity(Class<?> clazz) {
        return clazz.isAnnotationPresent(Entity.class);
    }


}
