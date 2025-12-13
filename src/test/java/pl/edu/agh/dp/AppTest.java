package pl.edu.agh.dp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.edu.agh.dp.api.Configuration;
import pl.edu.agh.dp.api.Orm;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.api.SessionFactory;
import pl.edu.agh.dp.api.annotations.Entity;

import java.sql.*;

/**
 * Unit test for simple App.
 */
public class AppTest {
    Configuration config = Orm.configure()
            .setProperty("db.url", "jdbc:sqlite:src/test/resources/test.db")
            .setProperty("orm.schema.auto", "dropTable-create");

    SessionFactory sessionFactory;
    Session session;

    @BeforeEach
    public void setUp() {
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
    }

    @AfterEach
    public void tearDown() {
        if (session != null) {
            session.close();
        }
        // TODO delete all tables ???
    }
    /**
     * Simple test to create and find
     */
    @Test
    public void testCreateTable() {
        User u = new User();
        u.setId(1);
        u.setName("Jan");
        u.setEmail("konrad@gmail.com");
        session.save(u);
        session.commit();

        User user = session.find(User.class, 1);
        assertEquals(user.getId(), u.getId());
        assertEquals(user.getEmail(), u.getEmail());
        assertEquals(user.getName(), u.getName());
    }
}
