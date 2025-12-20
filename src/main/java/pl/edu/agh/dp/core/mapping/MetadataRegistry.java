package pl.edu.agh.dp.core.mapping;

import lombok.Getter;
import pl.edu.agh.dp.api.annotations.DiscriminatorValue;
import pl.edu.agh.dp.api.annotations.Entity;
import pl.edu.agh.dp.api.annotations.Inheritance;

import java.util.*;

@Getter
public class MetadataRegistry {

    private Map<Class<?>, EntityMetadata> entities;

    public void build(List<Class<?>> entitiesClasses) {
        entities = new HashMap<>();

        for (Class<?> clazz : entitiesClasses){
            EntityMetadata entity =  MetadataBuilder.buildEntityMetadata(clazz);
            entities.put(clazz, entity);
        }

        // FIXME: better function call or move this part to MetadataBuilder - (change name for MetadataFactory)?
        handleInheritance();

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

    private void handleInheritance(){
        // get entity - add Inheritance Metadata and set type TODO maybe create special constructor for only type?
        for (Class<?> clazz : entities.keySet()) {
            EntityMetadata entity = entities.get(clazz);
            entity.setInheritanceMetadata(new InheritanceMetadata());
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

        // discriminator
        for (EntityMetadata m : entities.values()) {
            // Uruchamiamy logikę tylko dla Roota, bo on zarządza dyskryminatorem dla całej tabeli
            if (m.getInheritanceMetadata().isRoot() && !m.getInheritanceMetadata().getChildren().isEmpty() && m.getInheritanceMetadata().getType()==InheritanceType.SINGLE_TABLE) {
                handleDiscriminator(m);
            }
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

    private void handleDiscriminator(EntityMetadata root) {
        Class<?> rootClass = root.getEntityClass();
        InheritanceMetadata inhMetadata = root.getInheritanceMetadata();

        // 1. column name
        String discriminatorColName = "DTYPE";
        if (rootClass.isAnnotationPresent(DiscriminatorValue.class)) {
            discriminatorColName = rootClass.getAnnotation(DiscriminatorValue.class).value();
        }

        // save in metadata
        inhMetadata.setDiscriminatorColumnName(discriminatorColName);

        // 2. Stwórz "Wirtualną" kolumnę w metadanych Roota
        // Dzięki temu getColumnsForSingleTable() zwróci ją automatycznie do SQL-a
        PropertyMetadata discriminatorProperty = new PropertyMetadata();
        discriminatorProperty.setColumnName(discriminatorColName);
        discriminatorProperty.setType(String.class); // Zakładamy String
        discriminatorProperty.setSqlType("VARCHAR");
        discriminatorProperty.setIsId(false);
        // Ważne: to pole nie ma Field w Javie, więc generator SQL musi to obsłużyć
        // (nie próbować robić field.get() przy insertach w ciemno)

        root.getProperties().add(discriminatorProperty);

        // 3. Mapowanie Klasa <-> Wartość (dla roota i wszystkich dzieci)
        Map<Class<?>, String> classToDisc = new HashMap<>();
        Map<String, Class<?>> discToClass = new HashMap<>();

        // Zbieramy listę wszystkich klas w hierarchii (root + dzieci + dzieci dzieci...)
        List<EntityMetadata> allInHierarchy = new ArrayList<>();
        allInHierarchy.add(root);
        allInHierarchy.addAll(getAllSubclasses(root)); // Helper method needed

        for (EntityMetadata meta : allInHierarchy) {
            Class<?> clazz = meta.getEntityClass();
            String val;

            if (clazz.isAnnotationPresent(DiscriminatorValue.class)) {
                val = clazz.getAnnotation(DiscriminatorValue.class).value();
            } else {
                // Domyślnie: SimpleName (np. "Student", "Professor")
                val = clazz.getSimpleName();
            }

            classToDisc.put(clazz, val);
            discToClass.put(val, clazz);
        }

        // Zapisz mapy w metadanych Roota (to tam będziemy szukać przy ładowaniu danych)
        inhMetadata.setClassToDiscriminator(classToDisc);
        inhMetadata.setDiscriminatorToClass(discToClass);
        System.out.println("\nclass to disc " + inhMetadata.getClassToDiscriminator());
        System.out.println("\ndisc to class " + inhMetadata.getDiscriminatorToClass());
    }

    private List<EntityMetadata> getAllSubclasses(EntityMetadata root) {
        List<EntityMetadata> result = new ArrayList<>();
        Deque<EntityMetadata> stack = new ArrayDeque<>(root.getInheritanceMetadata().getChildren());
        while(!stack.isEmpty()){
            EntityMetadata current = stack.pop();
            result.add(current);
            stack.addAll(current.getInheritanceMetadata().getChildren());
        }
        return result;
    }

}
