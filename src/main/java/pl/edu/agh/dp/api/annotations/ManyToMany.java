package pl.edu.agh.dp.api.annotations;

public @interface ManyToMany {
    String mappedBy() default "";
    String joinTable() default "";
}
