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
import pl.edu.agh.dp.api.annotations.*;
import pl.edu.agh.dp.core.util.ReflectionUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RelationshipTest {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Department {
        @Id(autoIncrement = true)
        Long id;
        String name;

        @OneToMany
        List<Employee> employees;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Employee {
        @Id(autoIncrement = true)
        Long id;
        String name;

        @ManyToOne
        Department department;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DogOwner {
        @Id(autoIncrement = true)
        Long id;
        String name;
        @OneToOne
        Dog dog;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Dog {
        @Id
        Long id;
        @Column(defaultValue = "dog")
        String name;
        @OneToOne
        DogOwner dogOwner;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Worker {
        @Id(autoIncrement = true)
        Long id;
        String name;

        @OneToMany(mappedBy = "boss")
        @JoinColumn(joinColumns = { "workers" })  // TODO this should work
        List<Worker> workers;

        @ManyToOne(mappedBy = "workers")
        @JoinColumn(joinColumns = { "boss" }, nullable = true)
        Worker boss;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Student {
        @Id(autoIncrement = true)
        Long id;
        String name;

        @ManyToMany
        List<Course> courses = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Course {
        @Id(autoIncrement = true)
        Long id;
        String title;

        @ManyToMany
        List<Student> students = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Passport {
        @Id(autoIncrement = true)
        Long id;
        String name;

        @OneToOne
        @JoinColumn(joinColumns = { "name", "citizen" }, nullable = false)
        Citizen citizen;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Citizen {
        @Id(autoIncrement = true)
        Long id;
        String name;

        @OneToOne
        @JoinColumn(joinColumns = { "name", "passport" }, nullable = false)
        Passport passport;
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
    void testOneToOne() {
        config.register(DogOwner.class, Dog.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        {
            DogOwner dogOwner = new DogOwner();
            dogOwner.setName("Dave");

            Dog dog = new Dog();
            dog.setName("Nice");

            dog.setDogOwner(dogOwner);

            session.save(dog);
            session.commit();
        }

        {
            DogOwner dogOwner = new DogOwner();
            dogOwner.setName("Dave");

            Dog dog = new Dog();
            dog.setName("Nice");

            dogOwner.setDog(dog);

            session.save(dogOwner);
            session.commit();
        }

        {
            DogOwner dogOwner = new DogOwner();
            dogOwner.setName("Dave");

            Dog dog = new Dog();
            dog.setName("Nice");

            dogOwner.setDog(dog);
            dog.setDogOwner(dogOwner);

            session.save(dog);
            session.commit();
        }

        // Verify?
        // Ideally we would fetch back and check, but the original test just saved.
    }

    @Test
    void testManyToOne() {
        config.register(Department.class, Employee.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        {
            Department dept = new Department();
            dept.setName("IT");
            dept.setEmployees(new ArrayList<>());

            Employee emp1 = new Employee();
            emp1.setName("Alice");
            emp1.setDepartment(dept);

            Employee emp2 = new Employee();
            emp2.setName("Bob");
            emp2.setDepartment(dept);

            session.save(emp1);
            session.save(emp2);
            session.commit();
        }

        {
            Department dept = new Department();
            dept.setName("IT");

            Employee emp1 = new Employee();
            emp1.setName("Alice");

            Employee emp2 = new Employee();
            emp2.setName("Bob");

            dept.setEmployees(new ArrayList<>(){{add(emp1); add(emp2);}});

            session.save(dept);
            session.commit();
        }

        {
            Department dept = new Department();
            dept.setName("IT");

            Employee emp1 = new Employee();
            emp1.setName("Alice");

            Employee emp2 = new Employee();
            emp2.setName("Bob");

            dept.setEmployees(new ArrayList<>(){{add(emp1);}});

            emp2.setDepartment(dept);

            session.save(emp2);
            session.commit();
        }
        // TODO don't add existing entities
        // Validation could be added here
    }

    @Test
    void testSelfReference() {
        config.register(Worker.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        {
            Worker boss = new Worker();
            boss.setName("Boss");
            boss.setWorkers(new ArrayList<>());

            Worker subordinate = new Worker();
            subordinate.setName("Worker");
            subordinate.setBoss(boss);

            session.save(subordinate);
            session.commit();
        }

        {
            Worker boss = new Worker();
            boss.setName("Boss");
            boss.setWorkers(new ArrayList<>());
            session.save(boss);

            Worker subordinate = new Worker();
            subordinate.setName("Worker");
            subordinate.setBoss(boss);

            session.save(subordinate);
            session.commit();
        }

        {
            Worker boss = new Worker();
            boss.setName("Boss");
            boss.setWorkers(new ArrayList<>());

            Worker subordinate1 = new Worker();
            subordinate1.setName("Worker1");
            Worker subordinate2 = new Worker();
            subordinate2.setName("Worker2");
            subordinate1.setBoss(boss);
            subordinate2.setBoss(boss);

            session.save(subordinate1);
            session.save(subordinate2);
            session.commit();
        }

        {
            Worker boss = new Worker();
            boss.setName("Boss");

            Worker subordinate1 = new Worker();
            subordinate1.setName("Worker1");
            Worker subordinate2 = new Worker();
            subordinate2.setName("Worker2");

            boss.setWorkers(new ArrayList<>(){{add(subordinate1);add(subordinate2);}});

            session.save(boss);
            session.commit();
        }

        {
            Worker boss = new Worker();
            boss.setName("Boss");

            Worker subordinate1 = new Worker();
            subordinate1.setName("Worker1");
            Worker subordinate2 = new Worker();
            subordinate2.setName("Worker2");

            boss.setWorkers(new ArrayList<>(){{add(subordinate1);}});
            subordinate1.setWorkers(new ArrayList<>(){{add(subordinate2);}});

            session.save(boss);
            session.commit();
        }
    }

    @Test
    void testAddingExisting() {
        config.register(Worker.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Worker boss = new Worker();
        boss.setName("Boss");
        boss.setWorkers(new ArrayList<>());
        session.save(boss);
        session.commit();

        System.out.println("Adding worker to the boss.");

        Worker subordinate = new Worker();
        subordinate.setName("Worker");
        subordinate.setBoss(boss);

        session.save(subordinate);

        session.commit();
    }

    @Test
    void testManyToMany() {
        config.register(Student.class, Course.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Student student1 = new Student();
        student1.setName("John");

        Student student2 = new Student();
        student2.setName("Kowalski");

        Course course1 = new Course();
        course1.setTitle("Math");

        Course course2 = new Course();
        course2.setTitle("Physics");

        Course course3 = new Course();
        course3.setTitle("Computer Science");

//        session.save(course1);
//        session.save(course2);

        course3.setStudents(new ArrayList<>(){{
            add(student1); add(student2);
        }});

        student1.setCourses(new ArrayList<>(){{
            add(course1); add(course2);
        }});

        course1.setStudents(new ArrayList<>(){{
            add(student2);
        }});

        session.save(course3);
        session.commit();
    }

    @Test
    void testOneToOneJoinColumnOptions() {
        config.register(Citizen.class, Passport.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        // 1. Test success case
        Citizen citizen = new Citizen();
        citizen.setName("John");
        Passport passport = new Passport();
        passport.setName("John");
        citizen.setPassport(passport);

        // Assuming cascade save works for OneToOne or we save manually
        // If not, we might need: session.save(passport);

        session.save(citizen);
        session.commit();

        // 2. Test nullable=false failure
        // We need a fresh session or transaction usually, but let's try in same session

        Citizen citizen2 = new Citizen();
        citizen2.setName("Citizen Two (No Passport)");

        // Should fail because passport is nullable=false
        assertThrows(Exception.class, () -> {
            session.save(citizen2);
            session.commit();
        });
    }

    @Test
    void loadRelationship() {
        config.register(Department.class, Employee.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        Long departmentId;

        {
            Department dept = new Department();
            dept.setName("IT");
            dept.setEmployees(new ArrayList<>());

            Employee emp1 = new Employee();
            emp1.setName("Alice");
            emp1.setDepartment(dept);

            Employee emp2 = new Employee();
            emp2.setName("Bob");
            emp2.setDepartment(dept);

            session.save(emp1);
            session.save(emp2);
            session.commit();

            departmentId = dept.getId();
        }

        session.close();
        session = sessionFactory.openSession();

        Department dept = session.find(Department.class, departmentId);
        System.out.println(dept);
        session.load(dept, "employees");
        System.out.println(dept.getEmployees());
    }

    @Test
    void loadMany2Many() {
        config.register(Student.class, Course.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        Long id1;
        Long id2;
        {
            Student student1 = new Student();
            student1.setName("John");

            Student student2 = new Student();
            student2.setName("Kowalski");

            Course course1 = new Course();
            course1.setTitle("Math");

            Course course2 = new Course();
            course2.setTitle("Physics");

            Course course3 = new Course();
            course3.setTitle("Computer Science");

            course3.setStudents(new ArrayList<>() {{
                add(student1);
                add(student2);
            }});

            student1.setCourses(new ArrayList<>() {{
                add(course1);
                add(course2);
            }});

            course1.setStudents(new ArrayList<>() {{
                add(student2);
            }});

            session.save(course3);
            session.commit();
            id1 = student1.getId();
            id2 = student2.getId();
        }

        session.close();
        session = sessionFactory.openSession();
        List<Student> students = new ArrayList<>();

        Student student1 = session.find(Student.class, id1);
        student1.courses.size(); // load
//        session.load(student1, "courses");
        System.out.println(student1.getCourses());
        for (Course c : student1.getCourses()) {
            System.out.println(c.getId());
            System.out.println(c.getTitle());
            System.out.println(c.getStudents().size());
            if (c.getStudents().size() == 2 && students.isEmpty()) {
                students.addAll(c.getStudents());
            }
        }

        Student student2 = session.find(Student.class, id2);
        student2.courses.size();
        System.out.println(student2.getCourses());
        for (Course c : student2.getCourses()) {
            System.out.println(c.getId());
            System.out.println(c.getTitle());
        }

        System.out.println("final check");
        System.out.println(student1 + ", " + student2);
        System.out.println(students);
    }
}
