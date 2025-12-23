package pl.edu.agh.dp.core.mapping;

import lombok.Getter;
import pl.edu.agh.dp.api.annotations.DiscriminatorValue;
import pl.edu.agh.dp.api.annotations.Entity;
import pl.edu.agh.dp.api.annotations.Inheritance;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.util.*;
import java.lang.reflect.Field;

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
        // TODO maybe bundle up errors ?
        for (Class<?> clazz : entitiesClasses){
            fillAssociationData(clazz);
        }
    }

    public EntityMetadata getEntityMetadata(Class<?> clazz) {
        return entities.get(clazz);
    }

    private void fillAssociationData(Class<?> clazz) {
        EntityMetadata entityMetadata = entities.get(clazz);
        // no associations
        if (entityMetadata.getAssociationMetadata().isEmpty()) {
            return;
        }
        // TODO nullable fkey when the fkey is in the other table
        // TODO default factory
        // TODO load strategy (lazy, eager)
        // TODO load strategy (join, selectin)
        // TODO passive deletes
        // TODO on delete, on update, on insert
        // foreign keys are fkColumns - they are just field names, with some default keys settings
        // association columns are just placeholders with field names
        // remember to remove or change the idColumns if necessary
        // process associations:
        // check if target entity is in registry
        // check if the opposite association is unambiguous (mappedBy, only one exists)
        // check if all the join columns exist
        // check if one of join columns is it's own relationship (field)
        // check if join columns are the same
        // check if all the join columns are actually in the class
        // check if relationship has correct reverse type
        // check if not both of them are marked as Id
        // check if join could be determined or do we need join columns
        // TODO check other join columns for foreign keys
        for (AssociationMetadata currentAm : entityMetadata.getAssociationMetadata().values()) {
            // skip if the association is filled in (check target columns)
            if (!currentAm.getTargetJoinColumns().isEmpty()) {
                continue;
            }

            // determine the target entity
            Class<?> targetEntity = currentAm.getTargetEntity();

            // target does not exist
            if (!entities.containsKey(targetEntity)) {
                throw new IntegrityException(
                        "Missing entity in the registry.\n" +
                        "Relationship could not find the targeted entity in the registry.\n" +
                        "Source Class: " + clazz.getName() + "\n" +
                        "Source Field: " + currentAm.getField() + "\n" +
                        "Target entity: " + targetEntity.getName() + "\n" +
                        "Current registry: " + entities
                );
            }

            // get the opposite association
            EntityMetadata targetEntityMetadata = entities.get(targetEntity);
            List<AssociationMetadata> targetAms = new ArrayList<>();
            for (AssociationMetadata am2 : targetEntityMetadata.getAssociationMetadata().values()) {
                if (am2.getTargetEntity().equals(clazz)) {
                    targetAms.add(am2);
                }
            }
            // not found
            if (targetAms.isEmpty()) {
                throw new IntegrityException(
                        "Missing backward reference in the target entity.\n" +
                        "Relationship could not find the backwards reference in target entity.\n" +
                        "Source Class: " + clazz.getName() + "\n" +
                        "Source Field: " + currentAm.getField() + "\n" +
                        "Target entity: " + targetEntity.getName() + "\n" +
                        "Add backward relationship to the target entity or remove the relationship in the source class."
                );
            }
            AssociationMetadata targetAm = null;
            // try to use mapped by
            if (!currentAm.getMappedBy().isBlank()) {
                for (AssociationMetadata am : targetAms) {
                    if (am.getField().equals(currentAm.getMappedBy())) {
                        targetAm = am;
                        break;
                    }
                }
                if (targetAm == null) {
                    List<String> fieldNames = new ArrayList<>();
                    for (Field f : targetEntity.getDeclaredFields()) {
                        fieldNames.add(f.getName());
                    }
                    throw new IntegrityException(
                            "Mapped by column not found.\n" +
                            "Source Class: " + clazz.getName() + "\n" +
                            "Source mapped by: " + currentAm.getMappedBy() + "\n" +
                            "Target entity: " + targetEntity.getName() + "\n" +
                            "Found fields: " + String.join(", ", fieldNames) + "\n" +
                            "Change mappedBy in source class to one of those fields."
                    );
                }
            } else if (targetAms.size() > 1) {
                // check if relationship is ambiguous
                List<String> fieldNames = new ArrayList<>();
                for (AssociationMetadata am : targetAms) {
                    fieldNames.add(am.getField());
                }
                throw new IntegrityException(
                        "Ambiguous backward reference.\n" +
                        "Relationship could not determine the correct backward reference.\n" +
                        "Source Class: " + clazz.getName() + "\n" +
                        "Source Field: " + currentAm.getField() + "\n" +
                        "Target entity: " + targetEntity.getName() + "\n" +
                        "Found backward references: " + String.join(", ", fieldNames) + "\n" +
                        "Add 'mappedBy' parameter to the relationship with one of those found backward references."
                );
            } else {
                // size is 1, unambiguous
                targetAm = targetAms.get(0);
            }

            // check if all the join columns exist
            // check if one of join columns is it's own relationship (field)
            Set<String> currentJoinColumns = checkJoinColumns(clazz, currentAm);
            Set<String> targetJoinColumns = checkJoinColumns(targetEntity, targetAm);

            // check if join columns are the same
            if (!currentJoinColumns.containsAll(targetJoinColumns) || !targetJoinColumns.containsAll(currentJoinColumns)) {
                throw new IntegrityException(
                        "Join columns must be the same.\n" +
                        "Join columns of both classes should match.\n" +
                        "Source Class: " + clazz.getName() + "\n" +
                        "Relationship: " + currentAm.getField() + "\n" +
                        "Join columns: " + String.join(", ", currentJoinColumns) + "\n" +
                        "Target Class: " + targetEntity.getName() + "\n" +
                        "Relationship: " + targetAm.getField() + "\n" +
                        "Join columns: " + String.join(", ", targetJoinColumns)
                );
            }

            // check if relationship has correct reverse type
            // checked for not (list of correct types)
            if (!(currentAm.getType() == AssociationMetadata.Type.ONE_TO_ONE && targetAm.getType() == AssociationMetadata.Type.ONE_TO_ONE
               || currentAm.getType() == AssociationMetadata.Type.ONE_TO_MANY && targetAm.getType() == AssociationMetadata.Type.MANY_TO_ONE
               || currentAm.getType() == AssociationMetadata.Type.MANY_TO_ONE && targetAm.getType() == AssociationMetadata.Type.ONE_TO_MANY
               || currentAm.getType() == AssociationMetadata.Type.MANY_TO_MANY && targetAm.getType() == AssociationMetadata.Type.MANY_TO_MANY) ) {
                throw new IntegrityException(
                        "Relationships do not match.\n" +
                        "Source Class: " + clazz.getName() + "\n" +
                        "Relationship: " + currentAm.getField() + "\n" +
                        "Type of relationship: " + currentAm.getType() + "\n" +
                        "Target Class: " + targetEntity.getName() + "\n" +
                        "Relationship: " + targetAm.getField() + "\n" +
                        "Type of relationship: " + targetAm.getType() + "\n" +
                        "Valid relationships: ONE_TO_ONE <-> ONE_TO_ONE\n" +
                        "                    ONE_TO_MANY <-> MANY_TO_ONE\n" +
                        "                    MANY_TO_ONE <-> ONE_TO_MANY\n" +
                        "                   MANY_TO_MANY <-> MANY_TO_MANY"
                );
            }

            // determine where to put foreign keys
            boolean isForeignKeyOnCurrent;
            boolean isForeignKeyOnTarget;
            boolean isPrimaryKeyOnCurrent = currentAm.getFieldProperty().isId;
            boolean isPrimaryKeyOnTarget = targetAm.getFieldProperty().isId;

            if (currentAm.getType() == AssociationMetadata.Type.ONE_TO_ONE) {
                isForeignKeyOnCurrent = isPrimaryKeyOnCurrent;
                isForeignKeyOnTarget = isPrimaryKeyOnTarget;
                if (isForeignKeyOnCurrent && isForeignKeyOnTarget) {
                    throw new IntegrityException(
                            "Invalid field annotated with @Id.\n" +
                            "Source Class: " + clazz.getName() + "\n" +
                            "Relationship: " + currentAm.getField() + "\n" +
                            "Target Class: " + targetEntity.getName() + "\n" +
                            "Relationship: " + targetAm.getField() + "\n" +
                            "In one to one relationship at most one of them could be annotated with @Id."
                    );
                }
            } else if (currentAm.getType() == AssociationMetadata.Type.ONE_TO_MANY) {
                isForeignKeyOnCurrent = false;
                isForeignKeyOnTarget = true;
                if (isPrimaryKeyOnCurrent) {
                    throw new IntegrityException(
                            "Invalid field annotated with @Id.\n" +
                            "Source Class: " + clazz.getName() + "\n" +
                            "Relationship: " + currentAm.getField() + "\n" +
                            "Target Class: " + targetEntity.getName() + "\n" +
                            "Relationship: " + targetAm.getField() + "\n" +
                            "In one to many relationship only 'MANY' side could be annotated with @Id."
                    );
                }
            } else if (currentAm.getType() == AssociationMetadata.Type.MANY_TO_ONE) {
                isForeignKeyOnCurrent = true;
                isForeignKeyOnTarget = false;
                if (isPrimaryKeyOnTarget) {
                    throw new IntegrityException(
                            "Invalid field annotated with @Id.\n" +
                            "Source Class: " + clazz.getName() + "\n" +
                            "Relationship: " + currentAm.getField() + "\n" +
                            "Target Class: " + targetEntity.getName() + "\n" +
                            "Relationship: " + targetAm.getField() + "\n" +
                            "In many to one relationship only 'MANY' side could be annotated with @Id."
                    );
                }
            } else { // many to many relationship
                isForeignKeyOnCurrent = true;
                isForeignKeyOnTarget = true;
            }

            // check if join could be determined or do we need join columns
            // TODO idk what I meant ^

            // Everything is checked now and is correct

            // fix foreign keys (remove, change name, remove id)
            for (PropertyMetadata pm : currentAm.getJoinColumns()) {
                if (pm.getName().equals(currentAm.getField())) {
                    if (isForeignKeyOnCurrent) {
                        // TODO handle multiple primary keys in target entity
                        pm.setReferences(targetEntityMetadata.tableName + " (" + targetEntityMetadata.idColumns.get(0) + ")");
                    } else {

                    }
                }
            }

            // add constraints fk to SQL table
            // add NOT NULL constraint if necessary
            //
            // create association table for ManyToMany
            //
        }
    }

    private static Set<String> checkJoinColumns(Class<?> clazz, AssociationMetadata am) {
        // check if all the join columns exist
        // check if one of join columns is it's own relationship (field)
        Set<String> joinColumns = new HashSet<>();
        boolean hasItselfInJoin = false;
        for (PropertyMetadata joinColumn : am.getJoinColumns()) {
            if (!Objects.equals(joinColumn.getName(), am.getField())) {
                joinColumns.add(joinColumn.getName());
                hasItselfInJoin = true;
            }
            if (!ReflectionUtils.doesObjectContainField(clazz, joinColumn.getName())) {
                List<String> fieldNames = new ArrayList<>();
                for (Field f : clazz.getDeclaredFields()) {
                    fieldNames.add(f.getName());
                }
                throw new IntegrityException(
                        "Join column not found.\n" +
                                "All join columns should be name of the fields of the class.\n" +
                                "Source Class: " + clazz.getName() + "\n" +
                                "Found Fields: " + String.join(", ", fieldNames) + "\n" +
                                "Requested field: " + joinColumn.getName() + "\n" +
                                "Requested by relationship: " + am.getField()
                );
            }
        }
        if (!hasItselfInJoin) {
            throw new IntegrityException(
                    "Join columns should reference the field it's annotating.\n" +
                            "Source Class: " + clazz.getName() + "\n" +
                            "Join columns: " + String.join(", ", joinColumns) + "\n" +
                            "Relationship: " + am.getField()
            );
        }
        return joinColumns;
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
            if (m.getInheritanceMetadata().isRoot() && !m.getInheritanceMetadata().getChildren().isEmpty() && (m.getInheritanceMetadata().getType()==InheritanceType.SINGLE_TABLE || m.getInheritanceMetadata().getType()==InheritanceType.JOINED)) {
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
            return InheritanceType.TABLE_PER_CLASS;
        }
        return InheritanceType.TABLE_PER_CLASS; // FIXME: zastanawiam się co w przypadku braku dziedziczenia - NoInheritance???
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
        discriminatorProperty.setId(false);
        // Ważne: to pole nie ma Field w Javie, więc generator SQL musi to obsłużyć
        // (nie próbować robić field.get() przy insertach w ciemno)

        root.getProperties().put(discriminatorColName, discriminatorProperty); // FIXME idk if it works

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
