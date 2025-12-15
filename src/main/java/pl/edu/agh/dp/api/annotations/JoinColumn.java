package pl.edu.agh.dp.api.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Retention(RetentionPolicy.RUNTIME)
public @interface JoinColumn {
    boolean nullable() default false;
    String[] joinColumns() default {""};
}
