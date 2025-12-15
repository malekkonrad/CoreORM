package pl.edu.agh.dp.core.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InheritanceMetadata {

    private InheritanceType type;
    private String discriminatorColumnName; // tylko dla SINGLE_TABLE (opcjonalnie JOINED)

    // for SINGLE_TABLE
    private Map<Class<?>, String> classToDiscriminator;
    private Map<String, Class<?>> discriminatorToClass;

    private List<Class<?>> subclasses = new ArrayList<>();

    public <E> InheritanceMetadata(InheritanceType type, String discriminatorColumnName, String discriminatorColumnType, Map<Class<?>, String> classToDisc, Map<String, Class<?>> discToClass, List<Class<?>> of) {
        this.type = type;
        this.discriminatorColumnName = discriminatorColumnName;
        this.classToDiscriminator = classToDisc;
        this.discriminatorToClass = discToClass;
        this.subclasses = of;
//        this.subclasses = of;
    }


    @Override
    public String toString() {
        return "Inheritance " + type.toString() + " sub:" + subclasses.toString() + " dis " + discriminatorToClass.toString();
    }
}
