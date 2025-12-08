package pl.edu.agh.dp;


import pl.edu.agh.dp.api.Orm;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.api.SessionFactory;

public class App {
    public static void main(String[] args) {

        SessionFactory sf = Orm.configure()
                .setProperty("db.url", "jdbc:postgresql://localhost:5432/orm_demo")
                .setProperty("db.user", "orm_user")
                .setProperty("db.password", "secret")
                .setProperty("orm.schema.auto", "create") // <-- WŁĄCZA SchemaGenerator
                .buildSessionFactory();

        try (Session session = sf.openSession()) {
            User u = new User();
            u.setName("Jan");
            session.save(u);
        }
    }
}
