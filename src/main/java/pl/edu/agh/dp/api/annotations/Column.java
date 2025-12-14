package pl.edu.agh.dp.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    String columnName() default "";
    boolean nullable() default false;
    boolean unique() default false;
    boolean index() default false;
    String defaultValue() default ""; // TODO how to make it unset?

    int length() default 0;
    int scale() default 0;
    int precision() default 0;
}
