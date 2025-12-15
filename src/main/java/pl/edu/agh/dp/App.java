package pl.edu.agh.dp;

import pl.edu.agh.dp.api.Orm;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.api.SessionFactory;
import pl.edu.agh.dp.entities.Employee;
import pl.edu.agh.dp.entities.User;

public class App {
    public static void main(String[] args) {

        String dbUrl = System.getenv("DB_URL");
        if (dbUrl == null)
            dbUrl = "jdbc:postgresql://db:5432/orm_demo";

        String dbUser = System.getenv("DB_USER");
        if (dbUser == null)
            dbUser = "orm_user";

        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbPassword == null)
            dbPassword = "secret";

        SessionFactory sf = Orm.configure()
                .setProperty("db.url", dbUrl)
                .setProperty("db.user", dbUser)
                .setProperty("db.password", dbPassword)
                .register(User.class, Employee.class)
                .setProperty("orm.schema.auto", "drop-create") // <-- WŁĄCZA SchemaGenerator
                .buildSessionFactory();

        try (Session session = sf.openSession()) {
            User u = new User();
            u.setId(1L);
            u.setName("Jan");
            u.setEmail("konrad@gmail.com");
            session.save(u);
            session.commit();

            User user = session.find(User.class, 1);
            System.out.println(user.getEmail());
        }
    }
}
