package pl.edu.agh.dp.core.api;

public interface Configuration {
    Configuration setProperty(String s, String s1);
    Configuration register(Class<?>... entityClass);
    Configuration scanPackages(String... packages);
    SessionFactory buildSessionFactory();
}
