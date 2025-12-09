package pl.edu.agh.dp.api.annotations;

public @interface OneToMany {
    String mappedBy() default "";
}
