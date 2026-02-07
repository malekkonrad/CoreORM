package pl.edu.agh.dp.core.mapping.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Retention(RetentionPolicy.RUNTIME)
public @interface JoinColumn {
    // TODO add ondelete policy
    boolean nullable() default false;
    String[] joinColumns() default {""};
}
