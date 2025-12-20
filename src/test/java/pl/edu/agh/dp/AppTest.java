package pl.edu.agh.dp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.edu.agh.dp.api.Configuration;
import pl.edu.agh.dp.api.Orm;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.api.SessionFactory;
import pl.edu.agh.dp.entities.Employee;
import pl.edu.agh.dp.entities.User;

import java.sql.*;

/**
 * Unit test for simple App.
 */
public class AppTest {
    Configuration config = Orm.configure()
            .setProperty("db.url", "jdbc:sqlite:src/test/resources/test.db")
            .setProperty("orm.schema.auto", "drop-create");

    SessionFactory sessionFactory;
    Session session;

    @BeforeEach
    public void setUp() {

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
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:src/test/resources/test.db");
             Statement stmt = conn.createStatement()) {
//            stmt.execute("DELETE FROM users");
//            stmt.execute("DROP SCHEMA public CASCADE;");
//            stmt.execute("CREATE SCHEMA public;");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    /**
     * Simple test to create and find
     */
    @Test
    public void testCreateTable() {
        User u = new User();
        u.setId(1L);
        u.setName("Jan");
        u.setEmail("konrad@gmail.com");
        session.save(u);

        Employee e = new Employee();
        e.setId(2L);
//        e.setName("Konrad");
        e.setSalary(1000.0);
        session.save(e);
        session.commit();
        User user = session.find(User.class, 1L);
//        session.close();


        // Sprawdź bezpośrednio w bazie
//        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:src/test/resources/test.db");
//             Statement stmt = conn.createStatement();
//             ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE id = 1")) {
//            if (rs.next()) {
//                System.out.println("FOUND IN DB: id=" + rs.getLong("id") + ", name=" + rs.getString("name"));
//            } else {
//                System.out.println("NOT FOUND IN DB!");
//            }
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }



        assertEquals(u.getId(), user.getId());
        assertEquals(u.getEmail(), user.getEmail());
        assertEquals(u.getName(), user.getName());
    }
}
