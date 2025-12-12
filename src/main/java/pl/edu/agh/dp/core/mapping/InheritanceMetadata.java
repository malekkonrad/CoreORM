package pl.edu.agh.dp.core.mapping;

import java.util.Map;

public class InheritanceMetadata {

    boolean classTableInheritance;
    String baseTable;
    Map<Class<?>, String> subclassTables;
}
