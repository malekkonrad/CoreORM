package pl.edu.agh.dp.api.annotations;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public @interface JoinColumn {
    boolean nullable() default false;
    String[] joinColumns() default {""};
}
