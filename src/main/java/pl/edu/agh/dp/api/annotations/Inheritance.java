package pl.edu.agh.dp.api.annotations;

import pl.edu.agh.dp.core.mapping.InheritanceType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Inheritance {
    InheritanceType strategy() default InheritanceType.SINGLE_TABLE;
}
