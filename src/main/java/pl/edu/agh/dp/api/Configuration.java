package pl.edu.agh.dp.api;

public interface Configuration {
    Configuration setProperty(String s, String s1);
    SessionFactory buildSessionFactory();
}
