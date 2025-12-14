package pl.edu.agh.dp.core.mapping;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;


@Getter
@Setter
public class InheritanceMetadata {

    private final InheritanceType type;
    private final String discriminatorColumnName; // tylko dla SINGLE_TABLE (opcjonalnie JOINED)
    private final Map<Class<?>, String> classToDiscriminator;
    private final Map<String, Class<?>> discriminatorToClass;
    private final List<Class<?>> subclasses; // konkretne podklasy (bez root)

    public InheritanceMetadata(InheritanceType type,
                               String discriminatorColumnName,
                               Map<Class<?>, String> classToDiscriminator,
                               Map<String, Class<?>> discriminatorToClass,
                               List<Class<?>> subclasses) {
        this.type = type;
        this.discriminatorColumnName = discriminatorColumnName;
        this.classToDiscriminator = classToDiscriminator;
        this.discriminatorToClass = discriminatorToClass;
        this.subclasses = subclasses;
    }
}
