package pl.edu.agh.dp;

import entity.Department;
import entity.Employee;
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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

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

    @Test
    public void appTest() {
        config.register(
                Employee.class, Department.class
        );
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

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
        }

        {
            Employee employee = new Employee();
            employee.setFirstName("Jan2");
            employee.setLastName("Smith2");
            employee.setEmail("jan.smith2@gmail.com");
            employee.setPhone("1024957232");
            employee.setHireDate(LocalDate.now());
            employee.setSalary(new BigDecimal(34802));
            employee.setEmployeeCode("strhen");
            employee.setPosition("succ2");
            session.save(employee);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        List<Employee> employees = session.findAll(Employee.class);
        System.out.println(employees);
    }
}
