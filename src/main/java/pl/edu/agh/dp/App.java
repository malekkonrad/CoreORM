package pl.edu.agh.dp;

import pl.edu.agh.dp.api.Orm;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.api.SessionFactory;
import pl.edu.agh.dp.entities.Dog;
import pl.edu.agh.dp.entities.Employee;
import pl.edu.agh.dp.entities.Husky;
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
            Husky dog =  new Husky();
            dog.setName("Husky1");
            dog.setHow("How How");
            session.save(dog);

            Dog dog2 = new Dog();
            dog2.setName("Dog2");
            dog2.setAge(10);
            session.save(dog2);

            session.commit();

            System.out.println("ID dog: " + dog.getId());
            System.out.println(dog);
            Husky foundDog = session.find(Husky.class, dog.getId());
//            assertEquals(dog.getId(), foundDog.getId());
//            assertEquals(dog.getName(), foundDog.getName());
//            assertEquals(dog.getHow(), foundDog.getHow());

            foundDog.setHow("How How How");
            session.update(foundDog);
            session.commit();

            Husky found2Time = session.find(Husky.class, dog.getId());
            System.out.println(found2Time.getId() + " " + found2Time.getName());
//            assertEquals(foundDog.getId(), found2Time.getId());
//            assertEquals(foundDog.getName(), found2Time.getName());
//            assertEquals(foundDog.getHow(), found2Time.getHow());
        }
    }
}
