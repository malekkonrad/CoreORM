package pl.edu.agh.dp;

import entity.*;
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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ExampleAppTest {


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

    public void createEmployees() {
        Long bossId;
        Long sub1Id;
        Long sub2Id;

        {
            Employee employee = new Employee();
            employee.setFirstName("Jan");
            employee.setLastName("Smith");
            employee.setEmail("jan.smith@gmail.com");
            employee.setPhone("102495723");
            employee.setHireDate(LocalDate.now());
            employee.setSalary(new BigDecimal(3480));
            employee.setEmployeeCode("strhenrst");
            employee.setPosition("succ");
            session.save(employee);
            session.commit();
            bossId = employee.getId();
        }

        {
            Employee employee = new Employee();
            employee.setFirstName("Jan2");
            employee.setLastName("Smith2");
            employee.setEmail("jan.smith2@gmail.com");
            employee.setPhone("1024957232");
            employee.setHireDate(LocalDate.now());
            employee.setSalary(new BigDecimal(34802));
            employee.setEmployeeCode("strhen2");
            employee.setPosition("succ2");
            session.save(employee);
            session.commit();
            sub1Id = employee.getId();
        }

        {
            Employee employee = new Employee();
            employee.setFirstName("Jan3");
            employee.setLastName("Smith3");
            employee.setEmail("jan.smith3@gmail.com");
            employee.setPhone("1024957232");
            employee.setHireDate(LocalDate.now());
            employee.setSalary(new BigDecimal(34802));
            employee.setEmployeeCode("strhen3");
            employee.setPosition("succ3");
            session.save(employee);
            session.commit();
            sub2Id = employee.getId();
        }
        session.close();
        session = sessionFactory.openSession();
    }

    @Test
    public void findAllTest() {
        config.register(
                Employee.class, Department.class
        );
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long bossId = 1L;
        Long sub1Id = 2L;
        Long sub2Id = 3L;

        createEmployees();

        {
            List<Employee> employees = session.findAll(Employee.class);
            Employee employee = employees.iterator().next();
            assertNotNull(employee.getFirstName());
            assertEquals(LocalDate.now(), employee.getHireDate());
            assertEquals(3, employees.size());
        }
    }

    @Test
    public void addSubordinateTest() {
        config.register(
                Employee.class, Department.class
        );
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long bossId = 1L;
        Long sub1Id = 2L;
        Long sub2Id = 3L;

        createEmployees();

        {
            Employee boss = session.find(Employee.class, bossId);
            Employee employee2 = session.find(Employee.class, sub1Id);
            Employee employee3 = session.find(Employee.class, sub2Id);
            boss.setSubordinates(new ArrayList<>(){{
                add(employee2);add(employee3);
            }});
            session.update(boss);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            Employee boss = session.find(Employee.class, bossId);
            assertEquals(2, boss.getSubordinates().size());
        }
    }

    @Test
    public void addBossTest() {
        config.register(
                Employee.class, Department.class
        );
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long bossId = 1L;
        Long sub1Id = 2L;
        Long sub2Id = 3L;

        createEmployees();

        {
            Employee boss = session.find(Employee.class, bossId);
            Employee employee2 = session.find(Employee.class, sub1Id);
            Employee employee3 = session.find(Employee.class, sub2Id);
            employee2.setManager(boss);
            employee3.setManager(boss);
            session.update(employee2);
            session.update(employee3);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            Employee boss = session.find(Employee.class, bossId);
            assertEquals(2, boss.getSubordinates().size());
        }
    }

    @Test
    public void findNotificationTest() {
        config.register(
                Notification.class, PushNotification.class, EmailNotification.class
        );
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
    }

    @Test
    public void createBankAccountTest() {
        config.register(
                Account.class, BankAccount.class, InvestmentAccount.class, SavingsAccount.class
        );
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        {
            BankAccount bankAccount = new BankAccount();
            bankAccount.setAccountNumber("12345");
            bankAccount.setAccountName("tomas");
            bankAccount.setBalance(new BigDecimal(500));
            bankAccount.setOpenDate(LocalDate.now());
            bankAccount.setCurrency("EUR");

            bankAccount.setBankName("Bank Name");
            bankAccount.setIban("strhni");
            bankAccount.setSwift("strhen");
            bankAccount.setBranchCode("34508l");
            bankAccount.setHasDebitCard(false);
            bankAccount.setHasOnlineBanking(true);
            session.save(bankAccount);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            BankAccount bankAccount = session.find(BankAccount.class, 1L);
            assertEquals(bankAccount.getAccountNumber(), "12345");
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.__OLD_SINGLE)
    public static class Student {
        @Id(autoIncrement = true)
        Long id;
        String name;

        @ManyToMany
        @JoinColumn(nullable = true)
        List<Course> courses = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.__OLD_SINGLE)
    public static class Course {
        @Id(autoIncrement = true)
        Long id;
        String title;

        @ManyToMany
        @JoinColumn(nullable = true)
        List<Student> students = new ArrayList<>();
    }

    @Test
    public void updateMany2ManyTest() {
        config.register(
                Student.class, Course.class
        );
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long sid1;
        Long sid2;
        Long cid1;
        Long cid2;
        Long cid3;
        
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

            session.save(course1);
            session.save(course2);
            session.save(course3);
            session.save(student1);
            session.save(student2);
            session.commit();
            sid1 = student1.getId();
            sid2 = student2.getId();
            cid1 = course1.getId();
            cid2 = course2.getId();
            cid3 = course3.getId();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            Student student1 = session.find(Student.class, sid1);
            Course course1 = session.find(Course.class, cid1);

            student1.courses.add(course1);
            session.update(student1);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            Student student1 = session.find(Student.class, sid1);
            assertEquals(1, student1.getCourses().size());
            student1.courses.clear();
            session.update(student1);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            Student student1 = session.find(Student.class, sid1);
            assertEquals(0, student1.getCourses().size());
        }
    }
}
