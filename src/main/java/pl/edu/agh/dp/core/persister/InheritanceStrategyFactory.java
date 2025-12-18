package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.core.jdbc.Dialect;
import pl.edu.agh.dp.core.jdbc.JdbcExecutor;
import pl.edu.agh.dp.core.mapping.InheritanceType;
import pl.edu.agh.dp.core.mapping.MetadataRegistry;

public class InheritanceStrategyFactory {

//    private final JdbcExecutor jdbcExecutor;
//    private final Dialect dialect;
//    private final MetadataRegistry metadataRegistry;
//
//    public InheritanceStrategyFactory(JdbcExecutor jdbcExecutor,
//                                      Dialect dialect,
//                                      MetadataRegistry metadataRegistry) {
//        this.jdbcExecutor = jdbcExecutor;
//        this.dialect = dialect;
//        this.metadataRegistry = metadataRegistry;
//    }
//
//    public InheritanceStrategy build(InheritanceType type) {
//        return switch (type) {
//            case SINGLE_TABLE ->
//                    new SingleTableInheritanceStrategy(jdbcExecutor, dialect, metadataRegistry);
//            case JOINED -> {}
////                    new JoinedInheritanceStrategy(jdbcExecutor, dialect, metadataRegistry);
//            case TABLE_PER_CLASS -> {}
////                    new TablePerClassInheritanceStrategy(jdbcExecutor, dialect, metadataRegistry);
//            case NONE -> {}
////                    new NoInheritanceStrategy(jdbcExecutor, dialect, metadataRegistry);
//        };
//    }

    public static InheritanceStrategy build(){
        return new SingleTableInheritanceStrategy();
    }


}
