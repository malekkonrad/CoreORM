package pl.edu.agh.dp;

import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.edu.agh.dp.api.Configuration;
import pl.edu.agh.dp.api.Orm;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.api.SessionFactory;
import pl.edu.agh.dp.entities.*;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for simple App.
 */
public class AppTest {

    // Pobierz z ENV lub użyj domyślnej wartości (H2)
    String url = System.getenv("DB_URL") != null
            ? System.getenv("DB_URL")
            : "jdbc:h2:./testdb;AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

    String user = System.getenv("DB_USER") != null
            ? System.getenv("DB_USER")
            : "sa";

    String password = System.getenv("DB_PASSWORD") != null
            ? System.getenv("DB_PASSWORD")
            : "";

    Configuration config = Orm.configure()
            .setProperty("db.url", url)
            .setProperty("db.user", user)
            .setProperty("db.password", password)
            .setProperty("orm.schema.auto", "drop-create");

    SessionFactory sessionFactory;
    Session session;

    @BeforeEach
    public void setUp() {

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
//            stmt.execute("DELETE FROM users");
            stmt.execute("DELETE FROM animals");
            stmt.execute("DELETE FROM dogs");
            stmt.execute("DELETE FROM huskys");
//            stmt.execute("DROP SCHEMA public CASCADE;");
//            stmt.execute("CREATE SCHEMA public;");
        } catch (SQLException e) {
            e.printStackTrace();
        }


        config.register(User.class, Employee.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
    }

    @AfterEach
    public void tearDown() {
        if (session != null) {
            session.close();
        }
        // Wyczyść bazę danych
//        try (Connection conn = DriverManager.getConnection(url,  user, password);
//             Statement stmt = conn.createStatement()) {
////            stmt.execute("DELETE FROM users");
////            stmt.execute("DELETE FROM animals");
////            stmt.execute("DROP SCHEMA public CASCADE;");
////            stmt.execute("CREATE SCHEMA public;");
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
    }
    /**
     * Simple test to create and find
     */
//    @Test
//    public void testCreateTable() {
//        User u = new User();
//        u.setId(1L);
//        u.setName("Jan");
//        u.setEmail("konrad@gmail.com");
//        session.save(u);
//
//        Employee e = new Employee();
//        e.setId(2L);
////        e.setName("Konrad");
//        e.setSalary(1000.0);
//        session.save(e);
//
//        // animals:
//        Animal dog = new Dog();
//        dog.setId(1L);
//        dog.setName("Pies");
//        session.save(dog);
//
//        Animal dog2 =  new Dog();
//        dog2.setId(2L);
//        dog2.setName("Pies2");
//        session.save(dog2);
//
//
//        Husky dog3 =  new Husky();
//        dog3.setId(3L);
//        dog3.setName("Pies3");
//        dog3.setHow("How How");
//
//        session.save(dog3);
//
//
//        session.commit();
//        User user = session.find(User.class, 1L);
//        System.out.println(user.getName());
//        List<Dog> animals = session.findAll(Dog.class);
////        System.out.println(animals.toString());
//        for (Dog d : animals) {
//            System.out.println(d.getName());
//        }
//
//
//
//        Dog dog1234 = session.find(Dog.class, 2L);
//        System.out.println("animal 2l Name: " + dog1234.getName());
//
//        Husky husky = session.find(Husky.class, 3L);
//        System.out.println("Husky 3L: " + husky.getName());
//
//        session.delete(dog1234);
//        session.delete(user);
//        session.delete(husky);
//        session.commit();
//
//        Dog dogAfterDelete = session.find(Husky.class, 3L);
//        assertNull(dogAfterDelete);
//        User userAfetDelete =session.find(User.class, 1L);
//        assertNull(userAfetDelete);
////        Husky huskyAfterDelete = session.find(Husky.class, 3L);
////        assertNotNull(huskyAfterDelete);
//
//
//
//
////        System.out.println("animal 2l Name: " + dogAfterDelete.getName());
//
//
////        List<User> users =  session.findAll(User.class);
////        for (User userX : users) {
////            System.out.println(userX.getName());
////        }
// //        Dog dog123 =  session.find(Dog.class, 1L);
////        System.out.println(dog123.getId() + " " + dog123.getName());
//
////        session.close();
//
//        // Sprawdź bezpośrednio w bazie
////        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:src/test/resources/test.db");
////             Statement stmt = conn.createStatement();
////             ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
////            if (rs.next()) {
////                System.out.println("FOUND IN DB: id=" + rs.getLong("id") + ", name=" + rs.getString("name"));
////            } else {
////                System.out.println("NOT FOUND IN DB!");
////            }
////        } catch (SQLException e) {
////            e.printStackTrace();
////        }
//
//        assertEquals(u.getId(), user.getId());
//        assertEquals(u.getEmail(), user.getEmail());
//        assertEquals(u.getName(), user.getName());
//    }

//    @Test
    public void testCreateFindUpdate(){
        Husky dog =  new Husky();
//        dog.setId(1L);
        dog.setName("Dog1");
        dog.setHow("How How");

        session.save(dog);
        session.commit();

        Husky foundDog = session.find(Husky.class, dog.getId());
//        assertEquals(dog.getId(), foundDog.getId());
        assertEquals(dog.getName(), foundDog.getName());
        assertEquals(dog.getHow(), foundDog.getHow());

        foundDog.setHow("How How How");
        session.update(foundDog);
        session.commit();

        Husky found2Time = session.find(Husky.class, dog.getId());
//        assertEquals(foundDog.getId(), found2Time.getId());
        assertEquals(foundDog.getName(), found2Time.getName());
        assertEquals(foundDog.getHow(), found2Time.getHow());
    }

//    @Test
    public void testAutoIncrement(){
        Husky dog =  new Husky();
//        dog.setId(2L);
        dog.setName("Husky1");
        dog.setHow("How How");
        session.save(dog);

        Dog dog2 = new Dog();
//        dog2.setId(1L);
        dog2.setName("Dog2");
        dog2.setAge(10);
        session.save(dog2);

        Cat cat = new Cat();
//        cat.setId(4L);
        cat.setName("Cat1");
        cat.setCatName("CatName1");
        session.save(cat);

        session.commit();


//        try (Connection conn = DriverManager.getConnection(url, user, password);
//             Statement stmt = conn.createStatement();
//             ResultSet rs = stmt.executeQuery("SELECT * FROM huskys WHERE id = " + dog.getId())) {
//            if (rs.next()) {
//                System.out.println("FOUND IN DB: id=" + rs.getLong("id") + ", name=" + rs.getString("name") + ", how=" + rs.getString("how"));
//            } else {
//                System.out.println("NOT FOUND IN DB!");
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }



        System.out.println("ID dog: " + dog.getId());
        System.out.println(dog);
        Husky foundDog = session.find(Husky.class, dog.getId());
        assertEquals(dog.getId(), foundDog.getId());
        assertEquals(dog.getName(), foundDog.getName());
        assertEquals(dog.getHow(), foundDog.getHow());

        foundDog.setHow("How How How");
        session.update(foundDog);
        session.commit();

        Husky found2Time = session.find(Husky.class, dog.getId());
        assertEquals(foundDog.getId(), found2Time.getId());
        assertEquals(foundDog.getName(), found2Time.getName());
        assertEquals(foundDog.getHow(), found2Time.getHow());

//        session.delete(found2Time);
//        session.commit();
//
//        Husky deleted = session.find(Husky.class, dog.getId());
//        assertNull(deleted);

    }

    @Test
    public void testPolymorphicFindAll(){
        Husky dog =  new Husky();
        dog.setId(3L);
        dog.setName("Husky1");
        dog.setHow("How How");
        session.save(dog);

        Dog dog2 = new Dog();
        dog2.setId(1L);
        dog2.setName("Dog2");
        dog2.setAge(10);
        session.save(dog2);

        Cat cat = new Cat();
//        cat.setName("Cat1");
        cat.setId(4L);
        cat.setName("Cat1");
        cat.setCatName("CatName1");
        session.save(cat);

        session.commit();

        List<Animal> animals =  session.findAll(Animal.class);
        System.out.println(animals.toString());
        for (Animal animal:  animals) {
            System.out.println(animal.getName());
        }

        Dog foundDog = session.find(Dog.class, dog2.getId());
        assertEquals(dog2.getId(), foundDog.getId());
        System.out.println(foundDog.getName() + " " + foundDog.getId());
//        assertEquals(dogs.size(), 1);
//        assertEquals(dogs.get(0).getName(), dog2.getName());

    }


}
