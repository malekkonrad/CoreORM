package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.core.jdbc.Dialect;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.InheritanceType;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;

public class InheritanceStrategyFactory {

    public static InheritanceStrategy build(InheritanceType type, EntityMetadata metadata) {
        return switch (type){
            case SINGLE_TABLE -> new SingleTableInheritanceStrategy(metadata);
            case JOINED -> new JoinedTableInheritanceStrategy(metadata);
            case TABLE_PER_CLASS -> new TablePerClassInheritanceStrategy(metadata);
            case CONCRETE_CLASS ->  new ConcreteClassInheritanceStrategy(metadata);
            case NEW_SINGLE_TABLE -> new NewSingleTableInheritanceStrategy(metadata);
        };

    }
}
