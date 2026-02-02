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
import pl.edu.agh.dp.core.mapping.InheritanceType;
import pl.edu.agh.dp.demo.entity.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test demonstrujący wszystkie funkcjonalności CoreORM.
 * Każdy test definiuje własne klasy wewnętrzne (wzorowane na RelationshipTest).
 */
public class DemoEntityTest {

    String url = System.getenv("DB_URL") != null
            ? System.getenv("DB_URL")
            : "jdbc:h2:./testdb_demo;AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

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

    // ==================== JOINED INHERITANCE ====================
    
    @Test
    void testJoinedInheritance_EmployeeAndClient() {
        config.register(Person.class, Employee.class, Client.class, Department.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        // Tworzenie pracownika (extends Person)
        Employee employee = new Employee();
        employee.setFirstName("Jan");
        employee.setLastName("Kowalski");
        employee.setEmail("jan.kowalski@company.com");
        employee.setEmployeeCode("EMP001");

        session.save(employee);
        session.commit();

        // Tworzenie klienta (extends Person)
        Client client = new Client();
        client.setFirstName("Piotr");
        client.setLastName("Wiśniewski");
        client.setEmail("piotr@abccorp.com");
        client.setCompanyName("ABC Corporation");

        session.save(client);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        // Weryfikacja
        Employee foundEmployee = session.find(Employee.class, employee.getId());
        assertNotNull(foundEmployee);
        assertEquals("Jan", foundEmployee.getFirstName());
        assertEquals("EMP001", foundEmployee.getEmployeeCode());

        Client foundClient = session.find(Client.class, client.getId());
        assertNotNull(foundClient);
        assertEquals("ABC Corporation", foundClient.getCompanyName());

        // Polimorficzne wyszukiwanie
        List<Person> allPersons = session.findAll(Person.class);
        assertEquals(2, allPersons.size());
    }

    // ==================== SELF-REFERENCE ====================

    @Test
    void testSelfReference_EmployeeManagerHierarchy() {
        config.register(Person.class, Employee.class, Department.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        // Manager (bez przełożonego)
        Employee ceo = new Employee();
        ceo.setFirstName("Adam");
        ceo.setLastName("Prezes");
        ceo.setEmployeeCode("CEO001");
        ceo.setSubordinates(new ArrayList<>());

        // Podwładny CEO
        Employee manager1 = new Employee();
        manager1.setFirstName("Barbara");
        manager1.setLastName("Manager");
        manager1.setEmployeeCode("MGR001");
        manager1.setManager(ceo);

        session.save(manager1);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        // Weryfikacja hierarchii
        Employee foundManager = session.find(Employee.class, manager1.getId());
        session.load(foundManager, "manager");
        assertNotNull(foundManager.getManager());
        assertEquals("Adam", foundManager.getManager().getFirstName());
    }

    @Test
    void testSelfReference_DepartmentHierarchy() {
        config.register(Department.class, Person.class, Employee.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        // Główny departament
        Department mainDept = new Department();
        mainDept.setName("Firma XYZ");
        mainDept.setCode("MAIN");
        mainDept.setSubDepartments(new ArrayList<>());

        // Poddział
        Department itDept = new Department();
        itDept.setName("IT Department");
        itDept.setCode("IT");
        itDept.setParentDepartment(mainDept);

        session.save(itDept);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Department foundItDept = session.find(Department.class, itDept.getId());
        session.load(foundItDept, "parentDepartment");
        assertNotNull(foundItDept.getParentDepartment());
        assertEquals("Firma XYZ", foundItDept.getParentDepartment().getName());
    }

    // ==================== ONE-TO-MANY & MANY-TO-ONE ====================

    @Test
    void testOneToManyAndManyToOne_DepartmentEmployees() {
        config.register(Person.class, Employee.class, Department.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Department dept = new Department();
        dept.setName("Engineering");
        dept.setCode("ENG");
        dept.setEmployees(new ArrayList<>());

        Employee emp1 = new Employee();
        emp1.setFirstName("Filip");
        emp1.setLastName("Inżynier");
        emp1.setEmployeeCode("ENG001");
        emp1.setDepartment(dept);

        Employee emp2 = new Employee();
        emp2.setFirstName("Grażyna");
        emp2.setLastName("Tester");
        emp2.setEmployeeCode("ENG002");
        emp2.setDepartment(dept);

        session.save(emp1);
        session.save(emp2);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Department foundDept = session.find(Department.class, dept.getId());
        session.load(foundDept, "employees");
        
        assertNotNull(foundDept);
        assertEquals(2, foundDept.getEmployees().size());
    }

    // ==================== MANY-TO-MANY (osobne klasy wewnętrzne) ====================

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

    @Test
    void testManyToMany_StudentCourses() {
        config.register(Student.class, Course.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Student student1 = new Student();
        student1.setName("Jan");

        Student student2 = new Student();
        student2.setName("Anna");

        Course math = new Course();
        math.setTitle("Matematyka");

        Course physics = new Course();
        physics.setTitle("Fizyka");

        // Jan zapisuje się na oba kursy
        student1.getCourses().add(math);
        student1.getCourses().add(physics);

        // Anna tylko na matematykę
        math.getStudents().add(student2);

        session.save(student1);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Student foundStudent = session.find(Student.class, student1.getId());
        foundStudent.getCourses().size(); // trigger load
        assertEquals(2, foundStudent.getCourses().size());
    }

    // ==================== MULTIPLE RELATIONSHIPS (osobne klasy) ====================

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Worker {
        @Id(autoIncrement = true)
        Long id;
        String name;

        // Relacja 1: członek zespołu
        @ManyToMany
        List<Team> teams = new ArrayList<>();

        // Relacja 2: lider zespołu
        @OneToMany
        List<Team> ledTeams = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Team {
        @Id(autoIncrement = true)
        Long id;
        String name;

        @ManyToMany
        List<Worker> members = new ArrayList<>();

        @ManyToOne
        Worker leader;
    }

    @Test
    void testMultipleRelationships_WorkerTeam() {
        config.register(Worker.class, Team.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Worker leader = new Worker();
        leader.setName("Szef");

        Worker member1 = new Worker();
        member1.setName("Członek1");

        Worker member2 = new Worker();
        member2.setName("Członek2");

        Team team = new Team();
        team.setName("Zespół A");
        team.setLeader(leader);  // Relacja 1: lider
        team.setMembers(new ArrayList<>() {{ 
            add(leader);  // lider też jest członkiem
            add(member1);
            add(member2);
        }});  // Relacja 2: członkowie

        session.save(team);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        Team foundTeam = session.find(Team.class, team.getId());
        session.load(foundTeam, "leader");
        session.load(foundTeam, "members");

        assertNotNull(foundTeam.getLeader());
        assertEquals("Szef", foundTeam.getLeader().getName());
        assertEquals(3, foundTeam.getMembers().size());
    }

    // ==================== SINGLE_TABLE INHERITANCE ====================

    @Test
    void testSingleTableInheritance_Documents() {
        config.register(Document.class, Invoice.class, Report.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        // Faktura
        Invoice invoice = new Invoice();
        invoice.setTitle("Faktura za usługi IT");
        invoice.setCreatedDate(LocalDate.now());
        invoice.setInvoiceNumber("FV/2024/001");
        invoice.setTotalAmount(new BigDecimal("15000.00"));
        invoice.setPaymentStatus("PENDING");

        session.save(invoice);

        // Raport
        Report report = new Report();
        report.setTitle("Raport miesięczny");
        report.setCreatedDate(LocalDate.now());
        report.setReportType("MONTHLY");
        report.setStatus("DRAFT");

        session.save(report);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        // Polimorficzne wyszukiwanie
        List<Document> allDocs = session.findAll(Document.class);
        assertEquals(2, allDocs.size());

        Invoice foundInvoice = session.find(Invoice.class, invoice.getId());
        assertNotNull(foundInvoice);
        assertEquals("FV/2024/001", foundInvoice.getInvoiceNumber());
    }

    // ==================== TABLE_PER_CLASS INHERITANCE ====================

    @Test
    void testTablePerClassInheritance_Notifications() {
        config.register(Notification.class, EmailNotification.class, SmsNotification.class, PushNotification.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        // Email notification
        EmailNotification emailNotif = new EmailNotification();
        emailNotif.setTitle("Nowe zadanie");
        emailNotif.setMessage("Masz przypisane nowe zadanie.");
        emailNotif.setCreatedAt(LocalDateTime.now());
        emailNotif.setIsRead(false);
        emailNotif.setStatus("PENDING");
        emailNotif.setRecipientEmail("user@company.com");
        emailNotif.setSubject("Nowe zadanie");

        session.save(emailNotif);

        // SMS notification
        SmsNotification smsNotif = new SmsNotification();
        smsNotif.setTitle("Pilne spotkanie");
        smsNotif.setMessage("Spotkanie o 15:00");
        smsNotif.setCreatedAt(LocalDateTime.now());
        smsNotif.setIsRead(false);
        smsNotif.setStatus("PENDING");
        smsNotif.setPhoneNumber("123456789");

        session.save(smsNotif);

        // Push notification
        PushNotification pushNotif = new PushNotification();
        pushNotif.setTitle("Nowy komentarz");
        pushNotif.setMessage("Ktoś skomentował Twoje zadanie");
        pushNotif.setCreatedAt(LocalDateTime.now());
        pushNotif.setIsRead(false);
        pushNotif.setStatus("PENDING");
        pushNotif.setDeviceToken("abc123xyz789");
        pushNotif.setPlatform("ANDROID");

        session.save(pushNotif);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        // Weryfikacja
        EmailNotification foundEmail = session.find(EmailNotification.class, emailNotif.getId());
        assertNotNull(foundEmail);
        assertEquals("user@company.com", foundEmail.getRecipientEmail());

        SmsNotification foundSms = session.find(SmsNotification.class, smsNotif.getId());
        assertNotNull(foundSms);
        assertEquals("123456789", foundSms.getPhoneNumber());

        // Polimorficzne wyszukiwanie
        List<Notification> allNotifs = session.findAll(Notification.class);
        assertEquals(3, allNotifs.size());
    }

    // ==================== TASK SELF-REFERENCE ====================

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TaskItem {
        @Id(autoIncrement = true)
        Long id;
        String title;

        @OneToMany(mappedBy = "parentTask")
        @JoinColumn(joinColumns = {"subtasks"})
        List<TaskItem> subtasks = new ArrayList<>();

        @ManyToOne(mappedBy = "subtasks")
        @JoinColumn(joinColumns = {"parentTask"}, nullable = true)
        TaskItem parentTask;
    }

    @Test
    void testSelfReference_TaskHierarchy() {
        config.register(TaskItem.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        TaskItem mainTask = new TaskItem();
        mainTask.setTitle("Main Task");
        mainTask.setSubtasks(new ArrayList<>());

        TaskItem subTask1 = new TaskItem();
        subTask1.setTitle("Subtask 1");
        subTask1.setParentTask(mainTask);

        TaskItem subTask2 = new TaskItem();
        subTask2.setTitle("Subtask 2");
        subTask2.setParentTask(mainTask);

        session.save(subTask1);
        session.save(subTask2);
        session.commit();

        session.close();
        session = sessionFactory.openSession();

        TaskItem foundSubTask = session.find(TaskItem.class, subTask1.getId());
        session.load(foundSubTask, "parentTask");
        assertNotNull(foundSubTask.getParentTask());
        assertEquals("Main Task", foundSubTask.getParentTask().getTitle());
    }
}
