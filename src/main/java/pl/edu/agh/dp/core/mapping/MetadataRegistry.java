package pl.edu.agh.dp.core.mapping;

import lombok.Getter;
import pl.edu.agh.dp.core.mapping.annotations.DiscriminatorColumn;
import pl.edu.agh.dp.core.mapping.annotations.DiscriminatorValue;
import pl.edu.agh.dp.core.mapping.annotations.Entity;
import pl.edu.agh.dp.core.mapping.annotations.Inheritance;
import pl.edu.agh.dp.core.exceptions.IntegrityException;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.util.*;
import java.lang.reflect.Field;

@Getter
public class MetadataRegistry {

    private final Map<Class<?>, EntityMetadata> entities = new HashMap<>();

    public void build(List<Class<?>> entitiesClasses) {

        // create basic MetaData for each entity
        for (Class<?> clazz : entitiesClasses){
            EntityMetadata entity = MetadataBuilder.buildEntityMetadata(clazz);
            entities.put(clazz, entity);
        }

        // fill inheritance metadata based on current knowledge
        handleInheritance();

        // fill association metadata based on current knowledge
        for (Class<?> clazz : entitiesClasses){
            fillAssociationData(clazz);
        }

        // correct relationship after inheritance
        for (Class<?> clazz : entitiesClasses){
            EntityMetadata entity = entities.get(clazz);
            if (entity.getInheritanceMetadata().getType() == InheritanceType.TABLE_PER_CLASS) {
                entity.correctRelationshipsConcrete();
            } else if (entity.getInheritanceMetadata().getType() == InheritanceType.SINGLE_TABLE) {
                entity.correctRelationshipsSingle();
            } else if (entity.getInheritanceMetadata().getType() == InheritanceType.JOINED) {
                entity.correctRelationshipsJoined();
            } else if (entity.getInheritanceMetadata().getType() == InheritanceType.CONCRETE_CLASS) {
                entity.correctRelationshipsJoined();
            } else {
                throw new IntegrityException("Unhandled inheritance type: " + entity.getInheritanceMetadata().getType());
            }
        }

        // printing -> change for logging
        for (Class<?> clazz : entitiesClasses){
            EntityMetadata entity = entities.get(clazz);
            System.out.println(entity);
        }
    }

    private void fillAssociationData(Class<?> clazz) {
        EntityMetadata entityMetadata = entities.get(clazz);
        // no associations
        if (entityMetadata.getAssociationMetadata().isEmpty()) {
            return;
        }
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
        // check if not both of them are marked as Id when it's not many to many relationship
        // check if join could be determined or do we need join columns
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
                if (!Objects.equals(targetAm.getMappedBy(), currentAm.getField())) {
                    throw new IntegrityException(
                            "Mapped by columns do not match.\n" +
                            "Source Class: " + clazz.getName() + "\n" +
                            "Source mapped by: " + currentAm.getMappedBy() + "\n" +
                            "Target entity: " + targetEntity.getName() + "\n" +
                            "Target mapped by: " + targetAm.getMappedBy() + "\n" +
                            "Change mappedBy to match the other one."
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
                // set mappedBy
                targetAm.setMappedBy(currentAm.getField());
                currentAm.setMappedBy(targetAm.getField());
            }

            // check if all the join columns exist
            // check if one of join columns is it's own relationship (field)
            boolean areJoinColumnsSet = !(currentAm.getJoinColumns().isEmpty() && targetAm.getJoinColumns().isEmpty());
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

            // check if join could be determined or do we need join columns
            // assert that we are aware of other relationships (both have version, but it's not in joinColumn)
            if (!areJoinColumnsSet) {
                // check if there is other relationship
                List<AssociationMetadata> currentAssociations = new ArrayList<>(entityMetadata.getAssociationMetadata().values());
                List<AssociationMetadata> targetAssociations = new ArrayList<>(targetEntityMetadata.getAssociationMetadata().values());
                currentAssociations.remove(currentAm);
                targetAssociations.remove(targetAm);
                // get target entity from target's associations
                List<Class<?>> targetEntities = new ArrayList<>();
                for (AssociationMetadata am : targetAssociations) {
                   targetEntities.add(am.getTargetEntity());
                }

                List<AssociationMetadata> ambiguousAssociations = new ArrayList<>();
                for (AssociationMetadata am : currentAssociations) {
                   if (targetEntities.contains(am.getTargetEntity())) {
                       ambiguousAssociations.add(am);
                   }
                }
                // error if not empty
                if (!ambiguousAssociations.isEmpty()) {
                   throw new IntegrityException(
                           "Ambiguous relationship detected.\n" +
                           "Multiple relationships exist between source and target classes.\n" +
                           "Source Class: " + clazz.getName() + "\n" +
                           "Target Class: " + targetEntity.getName() + "\n" +
                           "Detected additional assosiations: " + ambiguousAssociations + "\n" +
                           "Please specify 'JoinColumns' to disambiguate the join condition."
                   );
                }
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
                if (!isForeignKeyOnCurrent && !isForeignKeyOnTarget) {
                    // choose at random
                    isForeignKeyOnCurrent = true;
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
                isForeignKeyOnCurrent = false;
                isForeignKeyOnTarget = false;
            }

            // fill target entity table name
            currentAm.setTargetTableName(targetAm.getTableName());
            targetAm.setTargetTableName(currentAm.getTableName());
            // set foreign key booleans for later
            currentAm.setHasForeignKey(isForeignKeyOnCurrent);
            targetAm.setHasForeignKey(isForeignKeyOnTarget);
            // fix foreign keys (remove, change name, remove id)
            if (isForeignKeyOnCurrent && !isForeignKeyOnTarget) {
                PropertyMetadata fkColumn = entityMetadata.fkColumns.get(currentAm.getField());
                List<PropertyMetadata> targetIdColumns = targetEntityMetadata.getNonForeignKeyColumns();

                if (targetIdColumns.isEmpty()) {
                    throw new IntegrityException("No primary keys to map to, idk what to do.");
                }

                if (targetIdColumns.size() == 1) {
                    fkColumn.setReferences(targetEntityMetadata.tableName + "(" + targetIdColumns.get(0).getColumnName() + ")");
                    fkColumn.setColumnName(fkColumn.getColumnName() + "_fkey");
                    fkColumn.setSqlType(getForeignSqlType(targetIdColumns.get(0)));

                    currentAm.setJoinColumns(new ArrayList<>(){{
                        add(fkColumn);
                        for (String field : currentJoinColumns) {
                            if (entityMetadata.getProperties().containsKey(field)) {
                                add(entityMetadata.getProperties().get(field));
                            } else if (entityMetadata.getFkColumns().containsKey(field)) {
                                add(entityMetadata.getFkColumns().get(field));
                            } else {
                                throw new IntegrityException("Invalid join columns, unable to determine them");
                            }
                        }
                    }});
                    AssociationMetadata finalTargetAm = targetAm;
                    targetAm.setJoinColumns(new ArrayList<>(){{
                        PropertyMetadata pm = targetIdColumns.get(0).clone();
                        pm.setName(finalTargetAm.getField());
                        add(pm);
                        for (String field : currentJoinColumns) {
                            if (targetEntityMetadata.getProperties().containsKey(field)) {
                                add(targetEntityMetadata.getProperties().get(field));
                            } else if (targetEntityMetadata.getFkColumns().containsKey(field)) {
                                add(targetEntityMetadata.getFkColumns().get(field));
                            } else {
                                throw new IntegrityException("Invalid join columns, unable to determine them");
                            }
                        }
                    }});
                    // remove fkey from target
                    targetEntityMetadata.fkColumns.remove(targetAm.getField());
                    // cross reference
                    currentAm.setTargetJoinColumns(targetAm.getJoinColumns());
                    targetAm.setTargetJoinColumns(currentAm.getJoinColumns());
                } else {
                    List<PropertyMetadata> fkColumns = new ArrayList<>();
                    List<PropertyMetadata> targetJoinColumnsNew = new ArrayList<>();
                    // create FK column for each PK
                    for (PropertyMetadata targetIdColumn : targetIdColumns) {
                        PropertyMetadata fkClone = fkColumn.clone();
                        fkClone.setColumnName(
                                fkColumn.getColumnName()
                                        + "_"
                                        + targetIdColumn.getColumnName()
                                        + "_fkey"
                        );
                        fkClone.setReferences(
                                targetEntityMetadata.tableName
                                        + "("
                                        + targetIdColumn.getColumnName()
                                        + ")"
                        );
                        fkClone.setSqlType(getForeignSqlType(targetIdColumn));
                        fkColumns.add(fkClone);

                        PropertyMetadata targetClone = targetIdColumn.clone();
                        targetClone.setName(targetAm.getField());

                        targetJoinColumnsNew.add(targetClone);
                    }

                    // add additional join columns (current side)
                    for (String field : currentJoinColumns) {
                        if (entityMetadata.getProperties().containsKey(field)) {
                            fkColumns.add(entityMetadata.getProperties().get(field));
                        } else if (entityMetadata.getFkColumns().containsKey(field)) {
                            fkColumns.add(entityMetadata.getFkColumns().get(field));
                        } else {
                            throw new IntegrityException("Invalid join columns, unable to determine them");
                        }
                    }

                    // add additional join columns (target side)
                    for (String field : currentJoinColumns) {
                        if (targetEntityMetadata.getProperties().containsKey(field)) {
                            targetJoinColumnsNew.add(targetEntityMetadata.getProperties().get(field));
                        } else if (targetEntityMetadata.getFkColumns().containsKey(field)) {
                            targetJoinColumnsNew.add(targetEntityMetadata.getFkColumns().get(field));
                        } else {
                            throw new IntegrityException("Invalid join columns, unable to determine them");
                        }
                    }
                    currentAm.setJoinColumns(fkColumns);
                    targetAm.setJoinColumns(targetJoinColumnsNew);
                    // remove fkey from target
                    targetEntityMetadata.fkColumns.remove(targetAm.getField());
                    fkColumn.setReferences(
                            targetEntityMetadata.tableName
                            + "("
                            + String.join(", ", targetIdColumns.stream().map(PropertyMetadata::getColumnName).toArray(String[]::new))
                            + ")"
                    );
                    fkColumn.setColumnName(String.join(", ", fkColumns.stream().map(PropertyMetadata::getColumnName).toArray(String[]::new)));
                    fkColumn.setSqlType(String.join(", ", fkColumns.stream().map(MetadataRegistry::getForeignSqlType).toArray(String[]::new)));
                    // cross reference
                    currentAm.setTargetJoinColumns(targetJoinColumnsNew);
                    targetAm.setTargetJoinColumns(fkColumns);
                }

            } else if (!isForeignKeyOnCurrent && isForeignKeyOnTarget) {
                PropertyMetadata targetFkColumn = targetEntityMetadata.fkColumns.get(targetAm.getField());
                List<PropertyMetadata> idColumns = entityMetadata.getNonForeignKeyColumns();

                if (idColumns.isEmpty()) {
                    throw new IntegrityException("No primary keys to map to, idk what to do.");
                }

                if (idColumns.size() == 1) {
                    targetFkColumn.setReferences(entityMetadata.tableName + "(" + idColumns.get(0).getColumnName() + ")");
                    targetFkColumn.setColumnName(targetFkColumn.getColumnName() + "_fkey");
                    targetFkColumn.setSqlType(getForeignSqlType(idColumns.get(0)));

                    targetAm.setJoinColumns(new ArrayList<>(){{
                        add(targetFkColumn);
                        for (String field : targetJoinColumns) {
                            if (targetEntityMetadata.getProperties().containsKey(field)) {
                                add(targetEntityMetadata.getProperties().get(field));
                            } else if (targetEntityMetadata.getFkColumns().containsKey(field)) {
                                add(targetEntityMetadata.getFkColumns().get(field));
                            } else {
                                throw new IntegrityException("Invalid join columns, unable to determine them");
                            }
                        }
                    }});
                    currentAm.setJoinColumns(new ArrayList<>(){{
                        PropertyMetadata pm = idColumns.get(0).clone();
                        pm.setName(currentAm.getField());
                        add(pm);
                        for (String field : targetJoinColumns) {
                            if (entityMetadata.getProperties().containsKey(field)) {
                                add(entityMetadata.getProperties().get(field));
                            } else if (entityMetadata.getFkColumns().containsKey(field)) {
                                add(entityMetadata.getFkColumns().get(field));
                            } else {
                                throw new IntegrityException("Invalid join columns, unable to determine them");
                            }
                        }
                    }});
                    // remove fkey from current
                    entityMetadata.fkColumns.remove(currentAm.getField());
                    // fill target join columns
                    currentAm.setTargetJoinColumns(targetAm.getJoinColumns());
                    targetAm.setTargetJoinColumns(currentAm.getJoinColumns());
                } else {
                    List<PropertyMetadata> targetFkColumns = new ArrayList<>();
                    List<PropertyMetadata> currentJoinColumnsNew = new ArrayList<>();

                    // create FK columns on target side for each PK
                    for (PropertyMetadata idColumn : idColumns) {

                        PropertyMetadata fkClone = targetFkColumn.clone();

                        fkClone.setColumnName(
                                targetFkColumn.getColumnName()
                                        + "_"
                                        + idColumn.getColumnName()
                                        + "_fkey"
                        );

                        fkClone.setReferences(
                                entityMetadata.tableName
                                        + "("
                                        + idColumn.getColumnName()
                                        + ")"
                        );
                        fkClone.setSqlType(getForeignSqlType(idColumn));
                        targetFkColumns.add(fkClone);

                        PropertyMetadata currentClone = idColumn.clone();
                        currentClone.setName(currentAm.getField());

                        currentJoinColumnsNew.add(currentClone);
                    }
                    // add additional join columns on target side
                    for (String field : targetJoinColumns) {
                        if (targetEntityMetadata.getProperties().containsKey(field)) {
                            targetFkColumns.add(targetEntityMetadata.getProperties().get(field));
                        } else if (targetEntityMetadata.getFkColumns().containsKey(field)) {
                            targetFkColumns.add(targetEntityMetadata.getFkColumns().get(field));
                        } else {
                            throw new IntegrityException("Invalid join columns, unable to determine them");
                        }
                    }
                    // add additional join columns on current side
                    for (String field : targetJoinColumns) {
                        if (entityMetadata.getProperties().containsKey(field)) {
                            currentJoinColumnsNew.add(entityMetadata.getProperties().get(field));
                        } else if (entityMetadata.getFkColumns().containsKey(field)) {
                            currentJoinColumnsNew.add(entityMetadata.getFkColumns().get(field));
                        } else {
                            throw new IntegrityException("Invalid join columns, unable to determine them");
                        }
                    }
                    targetAm.setJoinColumns(targetFkColumns);
                    currentAm.setJoinColumns(currentJoinColumnsNew);
                    // remove fkey from current
                    entityMetadata.fkColumns.remove(currentAm.getField());
                    targetFkColumn.setReferences(
                            entityMetadata.tableName
                                    + "("
                                    + String.join(", ", idColumns.stream().map(PropertyMetadata::getColumnName).toArray(String[]::new))
                                    + ")"
                    );
                    targetFkColumn.setColumnName(String.join(", ", targetFkColumns.stream().map(PropertyMetadata::getColumnName).toArray(String[]::new)));
                    targetFkColumn.setSqlType(String.join(", ", targetFkColumns.stream().map(MetadataRegistry::getForeignSqlType).toArray(String[]::new)));
                    // cross reference
                    currentAm.setTargetJoinColumns(targetFkColumns);
                    targetAm.setTargetJoinColumns(currentJoinColumnsNew);

                }
            } else if (!isForeignKeyOnCurrent && !isForeignKeyOnTarget) {
                PropertyMetadata targetFkColumn = targetEntityMetadata.fkColumns.get(targetAm.getField());
                List<PropertyMetadata> idColumns = entityMetadata.getNonForeignKeyColumns();
                PropertyMetadata fkColumn = entityMetadata.fkColumns.get(currentAm.getField());
                List<PropertyMetadata> targetIdColumns = targetEntityMetadata.getNonForeignKeyColumns();

                if (idColumns.isEmpty()) {
                    throw new IntegrityException("No primary keys to map to, idk what to do.");
                }
                if (targetIdColumns.isEmpty()) {
                    throw new IntegrityException("No primary keys to map to, idk what to do.");
                }

                if (idColumns.size() == 1 && targetIdColumns.size() == 1) {
                    EntityMetadata associationTable = new EntityMetadata();

                    // fill fk columns
                    fkColumn.setReferences(targetEntityMetadata.tableName + "(" + targetIdColumns.get(0).getColumnName() + ")");
                    fkColumn.setColumnName(fkColumn.getColumnName() + "_fkey");
                    currentAm.setJoinColumns(new ArrayList<PropertyMetadata>(){{add(fkColumn);}});
                    targetFkColumn.setReferences(entityMetadata.tableName + "(" + idColumns.get(0).getColumnName() + ")");
                    targetFkColumn.setColumnName(targetFkColumn.getColumnName() + "_fkey");
                    targetAm.setJoinColumns(new ArrayList<PropertyMetadata>(){{add(targetFkColumn);}});

                    Map<String, PropertyMetadata> columns = new HashMap<>();
                    columns.put(fkColumn.getName(), fkColumn);
                    columns.put(targetFkColumn.getName(), targetFkColumn);

                    associationTable.setTableName(entityMetadata.getTableName() + "_" + targetEntityMetadata.getTableName());
                    associationTable.setIdColumns(columns);
                    associationTable.setFkColumns(columns);

                    // set the dominant side of the relationship to the table per class
                    boolean preferTablePerClass = entityMetadata.getInheritanceMetadata().getType() == InheritanceType.TABLE_PER_CLASS;
                    currentAm.setHasForeignKey(preferTablePerClass);
                    targetAm.setHasForeignKey(!preferTablePerClass);

                    currentAm.setAssociationTable(associationTable);
                    targetAm.setAssociationTable(associationTable);

                    // remove fkey from current and target
                    entityMetadata.fkColumns.remove(currentAm.getField());
                    targetEntityMetadata.fkColumns.remove(targetAm.getField());
                    // fill join columns
                    currentAm.setTargetJoinColumns(targetAm.getJoinColumns());
                    targetAm.setTargetJoinColumns(currentAm.getJoinColumns());
                } else {

                    EntityMetadata associationTable = new EntityMetadata();

                    Map<String, PropertyMetadata> columns = new HashMap<>();

                    List<PropertyMetadata> currentJoinColumnsNew = new ArrayList<>();
                    List<PropertyMetadata> targetJoinColumnsList = new ArrayList<>();

                    // current side -> association table FK
                    for (PropertyMetadata targetIdColumn : targetIdColumns) {

                        PropertyMetadata fkClone = fkColumn.clone();

                        fkClone.setColumnName(
                                fkColumn.getColumnName()
                                        + "_"
                                        + targetIdColumn.getColumnName()
                                        + "_fkey"
                        );

                        fkClone.setReferences(
                                targetEntityMetadata.tableName
                                        + "("
                                        + targetIdColumn.getColumnName()
                                        + ")"
                        );
                        fkClone.setSqlType(getForeignSqlType(targetIdColumn));
                        columns.put(fkClone.getColumnName(), fkClone);

                        currentJoinColumnsNew.add(fkClone);
                    }
                    // target side -> association table FK
                    for (PropertyMetadata idColumn : idColumns) {

                        PropertyMetadata targetFkClone = targetFkColumn.clone();

                        targetFkClone.setColumnName(
                                targetFkColumn.getColumnName()
                                        + "_"
                                        + idColumn.getColumnName()
                                        + "_fkey"
                        );

                        targetFkClone.setReferences(
                                entityMetadata.tableName
                                        + "("
                                        + idColumn.getColumnName()
                                        + ")"
                        );
                        targetFkClone.setSqlType(getForeignSqlType(idColumn));
                        columns.put(targetFkClone.getColumnName(), targetFkClone);

                        targetJoinColumnsList.add(targetFkClone);
                    }

                    associationTable.setTableName(
                            entityMetadata.getTableName()
                                    + "_"
                                    + targetEntityMetadata.getTableName()
                    );
                    associationTable.setIdColumns(columns);
                    associationTable.setFkColumns(columns);

                    currentAm.setJoinColumns(currentJoinColumnsNew);
                    targetAm.setJoinColumns(targetJoinColumnsList);

                    // dominant side logic
                    boolean preferTablePerClass =
                            entityMetadata.getInheritanceMetadata().getType()
                                    == InheritanceType.TABLE_PER_CLASS;

                    currentAm.setHasForeignKey(preferTablePerClass);
                    targetAm.setHasForeignKey(!preferTablePerClass);

                    currentAm.setAssociationTable(associationTable);
                    targetAm.setAssociationTable(associationTable);
                    // remove original FK columns
                    entityMetadata.fkColumns.remove(currentAm.getField());
                    targetEntityMetadata.fkColumns.remove(targetAm.getField());
                    // cross reference
                    currentAm.setTargetJoinColumns(targetJoinColumnsList);
                    targetAm.setTargetJoinColumns(currentJoinColumnsNew);
                }

            } else {
                throw new IntegrityException("Unhandled relationship.");
            }
        }
    }

    private static String getForeignSqlType(PropertyMetadata pm) {
        String sqlType = pm.getSqlType();
        if (sqlType == "SERIAL") {
            sqlType = "INTEGER";
        } else if (sqlType == "BIGSERIAL") {
            sqlType = "BIGINT";
        } else if (sqlType == "SMALLSERIAL") {
            sqlType = "SMALLINT";
        }
        return sqlType;
    }

    private static Set<String> checkJoinColumns(Class<?> clazz, AssociationMetadata am) {
        // check if all the join columns exist
        // check if one of join columns is it's own relationship (field)
        Set<String> joinColumns = new HashSet<>();
        if (am.getJoinColumns().isEmpty()) {
            // if no join columns fill them with null
            am.setJoinColumns(new ArrayList<>(){{
                add(new PropertyMetadata(
                        am.getField(),
                        null,
                        null,
                        null,
                        false,
                        false,
                        false,
                        false,
                        false,
                        "__UNSET__",
                        null
                ));
            }});
            return joinColumns;
        }
        boolean hasItselfInJoin = false;
        for (PropertyMetadata joinColumn : am.getJoinColumns()) {
            if (Objects.equals(joinColumn.getName(), am.getField())) {
                hasItselfInJoin = true;
            } else {
                joinColumns.add(joinColumn.getName());
            }
            if (!ReflectionUtils.doesClassContainField(clazz, joinColumn.getName())) {
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
                        "Requested by relationship: " + am.getField() + "     \n"
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
        // get entity - add Inheritance Metadata and set type
        for (Class<?> clazz : entities.keySet()) {
            EntityMetadata entity = entities.get(clazz);
            entity.setInheritanceMetadata(new InheritanceMetadata());
            InheritanceType type = getStrategy(clazz);
            entity.getInheritanceMetadata().setType(type);
        }

        // set parent children dependency
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

        // check if children do not have new id properties
        for (EntityMetadata m : entities.values()) {
            if (!m.getInheritanceMetadata().isRoot()) {
                if (!m.getIdColumns().isEmpty()) {
                    throw new IntegrityException(
                            "Only root classes are allowed to have id's.\n" +
                            "Class: '" + m.getEntityClass().getName() + "' has additional id fields: " + m.getIdColumns().keySet() + "\n" +
                            "Remove them or move them to the root class."
                    );
                }
            }
        }

        // check if all the inheritance is matching in the inheritance tree
        for (EntityMetadata m : entities.values()) {
            if (!m.getInheritanceMetadata().isRoot()) {
                InheritanceType currentType = m.getInheritanceMetadata().getType();
                InheritanceType rootType = m.getInheritanceMetadata().getRootClass().getInheritanceMetadata().getType();
                if (currentType != rootType) {
                    throw new IntegrityException(
                            "Mismatching inheritance types.\n" +
                            "Class: '" + m.getEntityClass().getName() + "' has inheritance: " + currentType + "\n" +
                            "But it's root class: '" + m.getInheritanceMetadata().getRootClass().getEntityClass().getName() + "' has inheritance: " + rootType +"\n" +
                            "Inheritance strategy should match in the line of inheritance."
                    );
                }
            }
        }

        // discriminator
        for (EntityMetadata m : entities.values()) {
            // We run the logic only for Root, because it manages the discriminator for the
            // entire table
            if (m.getInheritanceMetadata().isRoot()
                    && (m.getInheritanceMetadata().getType() == InheritanceType.SINGLE_TABLE
                            || m.getInheritanceMetadata().getType() == InheritanceType.JOINED
                            || m.getInheritanceMetadata().getType() == InheritanceType.CONCRETE_CLASS)) {
                handleDiscriminator(m);
            }
        }

        // group together the columns
        for (EntityMetadata m : entities.values()) {
            InheritanceType type = m.getInheritanceMetadata().getType();
            if (type == InheritanceType.TABLE_PER_CLASS) {
                m.setMetadataForConcreteTable();
            } else if (type == InheritanceType.SINGLE_TABLE) {
                m.setMetadataForSingleTable();
            } else if (type == InheritanceType.JOINED) {
                m.addIdPropertyAll(m.getFkColumnsForJoinedTable());
                m.addFkPropertyAll(m.getFkColumnsForJoinedTable());
            } else if (type == InheritanceType.CONCRETE_CLASS) {
                m.setMetadataForConcreteClass();
                m.addIdPropertyAll(m.getFkColumnsForConcreteClass());
                m.addFkPropertyAll(m.getFkColumnsForConcreteClass());
            } else {
                throw new IntegrityException("Unhandled inheritance strategy: " + type);
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
        // Default TABLE_PER_CLASS
        if (clazz.getSuperclass() != Object.class && clazz.getSuperclass().isAnnotationPresent(Entity.class)) {
            return InheritanceType.SINGLE_TABLE;
        }
        return InheritanceType.SINGLE_TABLE;
    }

    private boolean isEntity(Class<?> clazz) {
        return clazz.isAnnotationPresent(Entity.class) || entities.containsKey(clazz);
    }

    private void handleDiscriminator(EntityMetadata root) {
        Class<?> rootClass = root.getEntityClass();
        InheritanceMetadata inhMetadata = root.getInheritanceMetadata();

        // TODO check if field DTYPE is in the class and warn about it

        // 1. column name
        String discriminatorColName = "DTYPE";
        if (rootClass.isAnnotationPresent(DiscriminatorColumn.class)) {
            discriminatorColName = rootClass.getAnnotation(DiscriminatorColumn.class).name();
        }

        // save in metadata
        inhMetadata.setDiscriminatorColumnName(discriminatorColName);

        // 2. Stwórz "Wirtualną" kolumnę w metadanych Roota
        // Dzięki temu getColumnsForSingleTable() zwróci ją automatycznie do SQL-a
        PropertyMetadata discriminatorProperty = new PropertyMetadata();
        discriminatorProperty.setName(discriminatorColName);
        discriminatorProperty.setColumnName(discriminatorColName);
        discriminatorProperty.setType(String.class); // Zakładamy String
        discriminatorProperty.setSqlType("VARCHAR");
        discriminatorProperty.setId(false);
        discriminatorProperty.setNullable(true); // DTYPE może być null dla niektórych strategii
        // Ważne: to pole nie ma Field w Javie, więc generator SQL musi to obsłużyć
        // (nie próbować robić field.get() przy insertach w ciemno)

        root.addProperty(discriminatorProperty);
//        root.getProperties().put(discriminatorColName, discriminatorProperty);

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
