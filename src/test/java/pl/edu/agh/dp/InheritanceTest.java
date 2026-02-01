package pl.edu.agh.dp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.edu.agh.dp.api.Configuration;
import pl.edu.agh.dp.api.Orm;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.api.SessionFactory;
import pl.edu.agh.dp.api.annotations.Id;
import pl.edu.agh.dp.api.annotations.Inheritance;
import pl.edu.agh.dp.core.mapping.InheritanceType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InheritanceTest {
    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.JOINED)
    public static class Animal {
        @Id(autoIncrement = true)
        Long id;
        String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Cat extends Animal {
        private String catName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Dog extends Animal {
        Integer age;
        String color;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Husky extends Dog {
        String how;
    }

    String url = System.getenv("DB_URL") != null
            ? System.getenv("DB_URL")
            : "jdbc:h2:./testdb_relationships;AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    String user = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "sa";
    String password = System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "";

    Configuration config;
    SessionFactory sessionFactory;
    Session session;

    Connection conn;
    Statement stmt;

    @BeforeEach
    public void setUp() {
        // Reset database
        try {
            conn = DriverManager.getConnection(url, user, password);
            stmt = conn.createStatement();
            stmt.execute("DROP ALL OBJECTS");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        config = Orm.configure()
                .setProperty("db.url", url)
                .setProperty("db.user", user)
                .setProperty("db.password", password)
                .setProperty("orm.schema.auto", "create");
    }

    @AfterEach
    public void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void testCreate() {
        config.register(Animal.class, Cat.class, Dog.class, Husky.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Husky dog = new Husky();
        dog.setName("Dog1");
        dog.setHow("How How");
        dog.setColor("black");
        dog.setAge(10);

        session.save(dog);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Husky foundDog = session.find(Husky.class, dog.getId());
         assertEquals(dog.getId(), foundDog.getId());
        assertEquals(dog.getName(), foundDog.getName());
        assertEquals(dog.getHow(), foundDog.getHow());
    }

    @Test
    void testUpdate() {
        config.register(Animal.class, Cat.class, Dog.class, Husky.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Husky dog = new Husky();
        dog.setName("Dog1");
        dog.setHow("How How");
        dog.setColor("black");
        dog.setAge(10);

        session.save(dog);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Husky foundDog = session.find(Husky.class, dog.getId());
        assertEquals(dog.getId(), foundDog.getId());
        assertEquals(dog.getName(), foundDog.getName());
        assertEquals(dog.getHow(), foundDog.getHow());

        foundDog.setHow("How How How");
        session.update(foundDog);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Husky found2Time = session.find(Husky.class, dog.getId());
        assertEquals(foundDog.getId(), found2Time.getId());
        assertEquals(foundDog.getName(), found2Time.getName());
        assertEquals(foundDog.getHow(), found2Time.getHow());
    }

    @Test
    void testDelete() {
        config.register(Animal.class, Cat.class, Dog.class, Husky.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Husky dog = new Husky();
        dog.setName("Dog1");
        dog.setHow("How How");
        dog.setColor("black");
        dog.setAge(10);

        session.save(dog);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Animal animal = session.find(Animal.class, dog.getId());
        assertEquals(dog.getId(), animal.getId());

        session.delete(animal);
        session.commit();

        Animal foundHusky = session.find(Husky.class, dog.getId());
        assertNull(foundHusky);

        session.close();
        session = sessionFactory.openSession();

        Animal foundHusky2 = session.find(Husky.class, dog.getId());
        assertNull(foundHusky2);
    }

    @Test
    void testFindAll() {
        config.register(Animal.class, Cat.class, Dog.class, Husky.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Husky dog = new Husky();
        dog.setName("Dog1");
        dog.setHow("How How");
        dog.setColor("black");
        dog.setAge(10);

        Cat cat = new Cat();
        cat.setCatName("my cat name");
        cat.setName("hehe");

        session.save(dog);
        session.save(cat);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        List<Animal> animals = session.findAll(Animal.class);
        assertEquals(animals.size(), 2);

        List<Husky> huskies = session.findAll(Husky.class);
        assertEquals(huskies.size(), 1);

        Husky h1 = huskies.get(0);
        assertTrue(animals.remove(h1));
        assertEquals(animals.size(), 1);
    }
}
