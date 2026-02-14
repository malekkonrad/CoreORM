package pl.edu.agh.dp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.edu.agh.dp.core.api.Configuration;
import pl.edu.agh.dp.core.api.Orm;
import pl.edu.agh.dp.core.api.Session;
import pl.edu.agh.dp.core.api.SessionFactory;
import pl.edu.agh.dp.core.mapping.InheritanceType;
import pl.edu.agh.dp.core.mapping.annotations.Id;
import pl.edu.agh.dp.core.mapping.annotations.Inheritance;
import pl.edu.agh.dp.core.mapping.annotations.JoinColumn;
import pl.edu.agh.dp.core.mapping.annotations.ManyToMany;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ComplexKeysTest {
    public static class Animal {
        @Id(autoIncrement = false)
        Long id;
        @Id(autoIncrement = false)
        Long id2;
        String name;
    }

    public static class Cat {
        String name;
    }

    @Getter
    @Setter
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    public static abstract class Person {
        @Id
        Long id;
        String name;
        String surname;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class Student extends Person {
        String index;

        @ManyToMany
        @JoinColumn(nullable = true)
        List<Subject> subjects;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class Subject {
        @Id
        Long id;
        String name;

        @ManyToMany
        @JoinColumn(nullable = true)
        List<Student> students;
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
    public void createCollectionTest() {
        config.register(Person.class, Student.class, Subject.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        {
            Student student = new Student();
            student.setName("name");
            student.setSurname("surname");
            student.setIndex("yes");

            Subject subject = new Subject();
            subject.setName("subject");
            subject.setStudents(List.of(student));

            session.save(subject);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            List<Person> persons = session.findAll(Person.class);

            Student student = session.find(Student.class, 1L);
            assertEquals(1, student.getSubjects().size());
            assertEquals("subject", student.getSubjects().get(0).getName());
        }
    }
}
