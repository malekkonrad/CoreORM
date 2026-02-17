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
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CONCRETE_CLASS inheritance strategy.
 *
 * Hierarchy:
 * Person (abstract) -> Student (concrete) -> Graduate (concrete)
 *
 * Expected behavior:
 * - Person has no table (abstract)
 * - Student table has Person fields + Student fields (merged)
 * - Graduate table has only Graduate fields + FK to Student (joined)
 */
public class ConcreteClassTest {

    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.CONCRETE_CLASS)
    public static abstract class Person {
        @Id(autoIncrement = true)
        Long id;
        String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Student extends Person {
        String university;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Graduate extends Student {
        String thesisTitle;
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
    void testTableStructure() throws SQLException {
        config.register(Person.class, Student.class, Graduate.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        // Verify Person table does NOT exist (abstract)
        ResultSet personTables = conn.getMetaData().getTables(null, null, "persons", null);
        assertFalse(personTables.next(), "Person table should NOT exist (abstract class)");

        // Verify Student table EXISTS and has Person's fields merged
        ResultSet studentTables = conn.getMetaData().getTables(null, null, "students", null);
        assertTrue(studentTables.next(), "Student table should exist");

        Set<String> studentColumns = getColumnNames("students");
        assertTrue(studentColumns.contains("id"), "Student should have id (from Person)");
        assertTrue(studentColumns.contains("name"), "Student should have name (from Person)");
        assertTrue(studentColumns.contains("university"), "Student should have university (own field)");

        // Verify Graduate table EXISTS and has only its own fields + FK
        ResultSet gradTables = conn.getMetaData().getTables(null, null, "graduates", null);
        assertTrue(gradTables.next(), "Graduate table should exist");

        Set<String> gradColumns = getColumnNames("graduates");
        assertTrue(gradColumns.contains("thesistitle") || gradColumns.contains("thesis_title"),
                "Graduate should have thesistitle (own field)");
        assertTrue(gradColumns.contains("id"), "Graduate should have id (FK to Student)");
        // Graduate should NOT have NAME or UNIVERSITY (those are in Student via joined)
        assertFalse(gradColumns.contains("name"), "Graduate should NOT have name (joined from Student)");
        assertFalse(gradColumns.contains("university"), "Graduate should NOT have university (joined from Student)");
    }

    private Set<String> getColumnNames(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, null);
        while (rs.next()) {
            columns.add(rs.getString("COLUMN_NAME").toLowerCase());
        }
        return columns;
    }

    @Test
    void testCreateStudent() {
        config.register(Person.class, Student.class, Graduate.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Student student = new Student();
        student.setName("Alice");
        student.setUniversity("AGH");

        session.save(student);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Student found = session.find(Student.class, student.getId());
        assertNotNull(found);
        assertEquals(student.getId(), found.getId());
        assertEquals("Alice", found.getName());
        assertEquals("AGH", found.getUniversity());
    }

    @Test
    void testCreateGraduate() {
        config.register(Person.class, Student.class, Graduate.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Graduate grad = new Graduate();
        grad.setName("Bob");
        grad.setUniversity("UJ");
        grad.setThesisTitle("ORM Design Patterns");

        session.save(grad);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Graduate found = session.find(Graduate.class, grad.getId());
        assertNotNull(found);
        assertEquals(grad.getId(), found.getId());
        assertEquals("Bob", found.getName());
        assertEquals("UJ", found.getUniversity());
        assertEquals("ORM Design Patterns", found.getThesisTitle());
    }

    @Test
    void testUpdate() {
        config.register(Person.class, Student.class, Graduate.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Graduate grad = new Graduate();
        grad.setName("Charlie");
        grad.setUniversity("PW");
        grad.setThesisTitle("First Thesis");

        session.save(grad);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Graduate found = session.find(Graduate.class, grad.getId());
        found.setThesisTitle("Updated Thesis");
        found.setName("Charlie Updated");
        session.update(found);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Graduate found2 = session.find(Graduate.class, grad.getId());
        assertEquals("Updated Thesis", found2.getThesisTitle());
        assertEquals("Charlie Updated", found2.getName());
    }

    @Test
    void testDelete() {
        config.register(Person.class, Student.class, Graduate.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Graduate grad = new Graduate();
        grad.setName("Dave");
        grad.setUniversity("UW");
        grad.setThesisTitle("Thesis to delete");

        session.save(grad);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Graduate found = session.find(Graduate.class, grad.getId());
        assertNotNull(found);

        session.delete(found);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Graduate deleted = session.find(Graduate.class, grad.getId());
        assertNull(deleted);
    }

    @Test
    void testFindAll() {
        config.register(Person.class, Student.class, Graduate.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Student student = new Student();
        student.setName("Eve");
        student.setUniversity("AGH");

        Graduate grad = new Graduate();
        grad.setName("Frank");
        grad.setUniversity("UJ");
        grad.setThesisTitle("AI Research");

        session.save(student);
        session.save(grad);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        List<Person> allPersons = session.findAll(Person.class);
        assertEquals(2, allPersons.size());

        List<Student> allStudents = session.findAll(Student.class);
        assertEquals(2, allStudents.size()); // Graduate is also a Student

        List<Graduate> allGraduates = session.findAll(Graduate.class);
        assertEquals(1, allGraduates.size());
        Graduate graduate = allGraduates.get(0);
        assertEquals("AI Research", graduate.getThesisTitle());
    }
}
