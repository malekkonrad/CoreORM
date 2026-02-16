package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.InheritanceType;

public class InheritanceStrategyFactory {

    public static InheritanceStrategy build(InheritanceType type, EntityMetadata metadata) {
        return switch (type){
            case __OLD_SINGLE -> new SingleTableInheritanceStrategy(metadata);
            case TABLE_PER_CLASS -> new JoinedTableInheritanceStrategy(metadata);
            case SINGLE_TABLE -> new TablePerClassInheritanceStrategy(metadata);
            case CONCRETE_CLASS ->  new ConcreteClassInheritanceStrategy(metadata);
        };

    }
}
