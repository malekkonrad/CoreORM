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
import pl.edu.agh.dp.core.mapping.annotations.*;

import java.io.Writer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ComplexKeysTest {

    @Getter
    @Setter
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    public static abstract class PersonSingle {
        @Id
        Long id;
        String name;
        String surname;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class StudentSingle extends PersonSingle {
        String index;

        @ManyToMany
        @JoinColumn(nullable = true)
        List<SubjectSingle> subjects;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    public static class SubjectSingle {
        @Id
        Long id;
        String name;

        @ManyToMany
        @JoinColumn(nullable = true)
        List<StudentSingle> students;
    }

    @Getter
    @Setter
    @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
    public static abstract class PersonTPC {
        @Id
        Long id;
        String name;
        String surname;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class StudentTPC extends PersonTPC {
        String index;

        @ManyToMany
        @JoinColumn(nullable = true)
        List<SubjectTPC> subjects;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
    public static class SubjectTPC {
        @Id
        Long id;
        String name;

        @ManyToMany
        @JoinColumn(nullable = true)
        List<StudentTPC> students;
    }

    @Getter
    @Setter
    @Inheritance(strategy = InheritanceType.JOINED)
    public static abstract class PersonJoined {
        @Id
        Long id;
        String name;
        String surname;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class StudentJoined extends PersonJoined {
        String index;

        @ManyToMany
        @JoinColumn(nullable = true)
        List<SubjectJoined> subjects;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    @Inheritance(strategy = InheritanceType.JOINED)
    public static class SubjectJoined {
        @Id
        Long id;
        String name;

        @ManyToMany
        @JoinColumn(nullable = true)
        List<StudentJoined> students;
    }
// LIBRARY -------------------------------------------------------
    @NoArgsConstructor
    @Getter
    @Setter
    public static class Library {
        @Id
        Long id;
        String name;

        @ManyToMany
        @JoinColumn(nullable = true)
        List<Reader> readers;

        @OneToMany
        @JoinColumn(nullable = true)
        List<Book> catalog;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class Reader {
        @Id(autoIncrement = false)
        String name;
        @Id(autoIncrement = false)
        String surname;

        @ManyToMany
        @JoinColumn(nullable = true)
        List<Library> libraries;

        @OneToMany
        @JoinColumn(nullable = true)
        List<Book> books;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class Book {
        @Id
        Long id;
        String title;
        @ManyToOne
        Library library;
        @ManyToOne
        Reader reader;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class Two {
        @Id(autoIncrement = false)
        Long id;
        @Id(autoIncrement = false)
        Long id2;
        @ManyToOne
        One one;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class One {
        @Id(autoIncrement = false)
        Long id;
        @Id(autoIncrement = false)
        Long id2;
        @OneToMany
        List<Two> two;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class OneId {
        Long id;
        Long id2;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class TwoM {
        @Id(autoIncrement = false)
        Long id;
        @Id(autoIncrement = false)
        Long id2;
        @ManyToMany
        List<OneM> one;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class OneM {
        @Id(autoIncrement = false)
        Long id;
        @Id(autoIncrement = false)
        Long id2;
        @ManyToMany
        List<TwoM> two;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class TwoString {
        @Id(autoIncrement = false)
        String id;
        @ManyToOne
        OneString oneString;
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class OneString {
        @Id(autoIncrement = false)
        String id;
        @OneToMany
        List<TwoString> twoStrings;
    }


// ---------------------------------------------------------------

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
    public void stringKeyTest() {
        config.register(TwoString.class, OneString.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        {
            TwoString two = new TwoString();
            two.setId("two");

            OneString one = new OneString();
            one.setId("one");

            two.setOneString(one);

            session.save(two);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        OneString one = session.find(OneString.class, "one");
        assertEquals(1, one.getTwoStrings().size());
    }

    @Test
    public void complexKeyTest() {
        config.register(Two.class, One.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        {
            Two two = new Two();
            two.setId(1L);
            two.setId2(2L);

            One one = new One();
            one.setId(3L);
            one.setId2(4L);

            two.setOne(one);

            session.save(two);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        OneId oneId = new OneId();
        oneId.setId(3L);
        oneId.setId2(4L);

        One one = session.find(One.class, oneId);
        assertEquals(1, one.getTwo().size());
    }

    @Test
    public void complexKeyManyToManyTest() {
        config.register(TwoM.class, OneM.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        {
            TwoM two = new TwoM();
            two.setId(1L);
            two.setId2(2L);

            OneM one = new OneM();
            one.setId(3L);
            one.setId2(4L);

            two.setOne(new ArrayList<>(){{add(one);}});

            session.save(two);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        OneId oneId = new OneId();
        oneId.setId(3L);
        oneId.setId2(4L);

        OneM one = session.find(OneM.class, oneId);
        assertEquals(1, one.getTwo().size());
    }

    @Test
    public void loadSingleTest() {
        config.register(PersonSingle.class, StudentSingle.class, SubjectSingle.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        {
            StudentSingle student = new StudentSingle();
            student.setName("name");
            student.setSurname("surname");
            student.setIndex("yes");

            SubjectSingle subject = new SubjectSingle();
            subject.setName("subject");
            subject.setStudents(List.of(student));

            session.save(subject);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            List<PersonSingle> persons = session.findAll(PersonSingle.class);

            StudentSingle student = session.find(StudentSingle.class, 1L);
            assertEquals(1, student.getSubjects().size());
            assertEquals("subject", student.getSubjects().get(0).getName());
        }
    }

    @Test
    public void loadTPCTest() {
        config.register(PersonTPC.class, StudentTPC.class, SubjectTPC.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        {
            StudentTPC student = new StudentTPC();
            student.setName("name");
            student.setSurname("surname");
            student.setIndex("yes");

            SubjectTPC subject = new SubjectTPC();
            subject.setName("subject");
            subject.setStudents(List.of(student));

            session.save(subject);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            List<PersonTPC> persons = session.findAll(PersonTPC.class);

            StudentTPC student = session.find(StudentTPC.class, 1L);
            assertEquals(1, student.getSubjects().size());
            assertEquals("subject", student.getSubjects().get(0).getName());
        }
    }

    @Test
    public void loadJoinedTest() {
        config.register(PersonJoined.class, StudentJoined.class, SubjectJoined.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        {
            StudentJoined student = new StudentJoined();
            student.setName("name");
            student.setSurname("surname");
            student.setIndex("yes");

            SubjectJoined subject = new SubjectJoined();
            subject.setName("subject");
            subject.setStudents(List.of(student));

            session.save(subject);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            List<PersonJoined> persons = session.findAll(PersonJoined.class);

            StudentJoined student = session.find(StudentJoined.class, 1L);
            assertEquals(1, student.getSubjects().size());
            assertEquals("subject", student.getSubjects().get(0).getName());
        }
    }

    // LIBRARY TESTS -----------------------------------------------------------

    @Test
    public void findAllRentedBooksTest() {
        config.register(Library.class, Reader.class, Book.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long libraryId;
        {
            Library library = new Library();
            library.setName("library");

            Reader reader1 = new Reader();
            reader1.setName("Kamil");
            reader1.setSurname("Pustelnik");
            reader1.setLibraries(List.of(library));

            Reader reader2 = new Reader();
            reader2.setName("Konrad");
            reader2.setSurname("Małek");
            reader2.setLibraries(List.of(library));

            Reader reader3 = new Reader();
            reader3.setName("Mateusz");
            reader3.setSurname("Kotarba");
            reader3.setLibraries(List.of(library));

            Book book1 = new Book();
            book1.setTitle("HibernateDocs");
            book1.setLibrary(library);

            Book book2 = new Book();
            book2.setTitle("OrmForDummies");
            book2.setLibrary(library);

            Book book3 = new Book();
            book3.setTitle("HowToNotKillYourself");
            book3.setLibrary(library);

            reader1.setBooks(List.of(book1));
            reader2.setBooks(List.of(book2));
            reader3.setBooks(List.of(book3));

            session.save(reader1);
            session.save(reader2);
            session.save(reader3);

            session.commit();

            libraryId = library.getId();
        }

        session.close();
        session = sessionFactory.openSession();

        Library library1 = session.find(Library.class, libraryId);
        assertEquals(library1.getName(), "library");
    }
}
