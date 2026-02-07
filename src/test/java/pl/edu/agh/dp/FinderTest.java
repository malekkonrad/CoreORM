package pl.edu.agh.dp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.*;
import pl.edu.agh.dp.core.api.Configuration;
import pl.edu.agh.dp.core.api.Orm;
import pl.edu.agh.dp.core.api.Session;
import pl.edu.agh.dp.core.api.SessionFactory;
import pl.edu.agh.dp.core.mapping.annotations.Column;
import pl.edu.agh.dp.core.mapping.annotations.Id;
import pl.edu.agh.dp.core.mapping.annotations.Inheritance;
import pl.edu.agh.dp.core.mapping.annotations.Table;
import pl.edu.agh.dp.core.mapping.InheritanceType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Finder fluent API
 */
public class FinderTest {

    @Getter
    @Setter
    @NoArgsConstructor
    @Table(name = "products")
    public static class Product {
        @Id(autoIncrement = true)
        Long id;
        
        @Column(columnName = "name")
        String name;
        
        @Column(columnName = "category")
        String category;
        
        @Column(columnName = "price")
        Double price;
        
        @Column(columnName = "quantity")
        Integer quantity;
        
        @Column(columnName = "active")
        Boolean active;
        
        public Product(String name, String category, Double price, Integer quantity, Boolean active) {
            this.name = name;
            this.category = category;
            this.price = price;
            this.quantity = quantity;
            this.active = active;
        }
    }

    String url = System.getenv("DB_URL") != null
            ? System.getenv("DB_URL")
            : "jdbc:h2:./testdb_finder;AUTO_SERVER=TRUE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

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
    void testFinderBasicEquality() {
        config.register(Product.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        // Insert test data
        session.save(new Product("Laptop", "Electronics", 999.99, 10, true));
        session.save(new Product("Phone", "Electronics", 599.99, 25, true));
        session.save(new Product("Book", "Books", 29.99, 100, true));
        session.save(new Product("Old Phone", "Electronics", 199.99, 5, false));
        session.commit();

        // Test basic equality
        List<Product> electronics = session.finder(Product.class)
                .eq("category", "Electronics")
                .list();

        for (var product: electronics){
            System.out.println(product.getName() + " " + product.getCategory() + " " + product.getPrice() + " " + product.getQuantity() + " " + product.getActive());
        }

        assertEquals(3, electronics.size());
        assertTrue(electronics.stream().allMatch(p -> "Electronics".equals(p.getCategory())));
    }

    @Test
    void testFinderGreaterThan() {
        config.register(Product.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        session.save(new Product("Cheap", "Test", 10.0, 1, true));
        session.save(new Product("Medium", "Test", 50.0, 1, true));
        session.save(new Product("Expensive", "Test", 100.0, 1, true));
        session.commit();

        List<Product> expensive = session.finder(Product.class)
                .gt("price", 30.0)
                .list();

        assertEquals(2, expensive.size());
        assertTrue(expensive.stream().allMatch(p -> p.getPrice() > 30.0));
    }

    @Test
    void testFinderLike() {
        config.register(Product.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        session.save(new Product("iPhone 12", "Phones", 800.0, 1, true));
        session.save(new Product("iPhone 13", "Phones", 900.0, 1, true));
        session.save(new Product("Samsung Galaxy", "Phones", 750.0, 1, true));
        session.commit();

        List<Product> iphones = session.finder(Product.class)
                .like("name", "iPhone%")
                .list();

        assertEquals(2, iphones.size());
        assertTrue(iphones.stream().allMatch(p -> p.getName().startsWith("iPhone")));
    }

    @Test
    void testFinderMultipleConditions() {
        config.register(Product.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        session.save(new Product("Product1", "Cat1", 100.0, 10, true));
        session.save(new Product("Product2", "Cat1", 200.0, 20, true));
        session.save(new Product("Product3", "Cat1", 300.0, 30, false));
        session.save(new Product("Product4", "Cat2", 150.0, 15, true));
        session.commit();

        // Multiple conditions: category = 'Cat1' AND price > 150 AND active = true
        List<Product> results = session.finder(Product.class)
                .eq("category", "Cat1")
                .gt("price", 150.0)
                .eq("active", true)
                .list();

        assertEquals(1, results.size());
        assertEquals("Product2", results.get(0).getName());
    }

    @Test
    void testFinderOrderBy() {
        config.register(Product.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        session.save(new Product("B", "Test", 20.0, 1, true));
        session.save(new Product("A", "Test", 30.0, 1, true));
        session.save(new Product("C", "Test", 10.0, 1, true));
        session.commit();

        // Order by name ascending
        List<Product> ascOrder = session.finder(Product.class)
                .orderAsc("name")
                .list();

        assertEquals(3, ascOrder.size());
        assertEquals("A", ascOrder.get(0).getName());
        assertEquals("B", ascOrder.get(1).getName());
        assertEquals("C", ascOrder.get(2).getName());

        // Order by price descending
        List<Product> descOrder = session.finder(Product.class)
                .orderDesc("price")
                .list();

        assertEquals(3, descOrder.size());
        assertEquals(30.0, descOrder.get(0).getPrice());
        assertEquals(20.0, descOrder.get(1).getPrice());
        assertEquals(10.0, descOrder.get(2).getPrice());
    }

    @Test
    void testFinderLimitAndOffset() {
        config.register(Product.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        for (int i = 1; i <= 10; i++) {
            session.save(new Product("Product" + i, "Test", i * 10.0, 1, true));
        }
        session.commit();

        // Limit to 3 results
        List<Product> limited = session.finder(Product.class)
                .orderAsc("name")
                .limit(3)
                .list();

        assertEquals(3, limited.size());

        // Pagination: skip first 3, take next 3
        List<Product> page2 = session.finder(Product.class)
                .orderAsc("name")
                .offset(3)
                .limit(3)
                .list();

        assertEquals(3, page2.size());
    }

    @Test
    void testFinderFirstAndSingle() {
        config.register(Product.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        session.save(new Product("Unique", "Test", 99.99, 1, true));
        session.save(new Product("Other", "Test", 50.0, 1, true));
        session.commit();

        // first() returns Optional
        Optional<Product> first = session.finder(Product.class)
                .eq("name", "Unique")
                .first();

        assertTrue(first.isPresent());
        assertEquals("Unique", first.get().getName());

        // single() returns exactly one or throws
        Product single = session.finder(Product.class)
                .eq("name", "Unique")
                .single();

        assertEquals("Unique", single.getName());

        // first() with no results
        Optional<Product> notFound = session.finder(Product.class)
                .eq("name", "NonExistent")
                .first();

        assertTrue(notFound.isEmpty());
    }

    @Test
    void testFinderIn() {
        config.register(Product.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        session.save(new Product("P1", "Cat1", 10.0, 1, true));
        session.save(new Product("P2", "Cat2", 20.0, 1, true));
        session.save(new Product("P3", "Cat3", 30.0, 1, true));
        session.commit();

        List<Product> results = session.finder(Product.class)
                .in("category", Arrays.asList("Cat1", "Cat3"))
                .list();

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(p -> "Cat1".equals(p.getCategory())));
        assertTrue(results.stream().anyMatch(p -> "Cat3".equals(p.getCategory())));
    }

    @Test
    void testFinderBetween() {
        config.register(Product.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        session.save(new Product("P1", "Test", 10.0, 1, true));
        session.save(new Product("P2", "Test", 50.0, 1, true));
        session.save(new Product("P3", "Test", 100.0, 1, true));
        session.save(new Product("P4", "Test", 150.0, 1, true));
        session.commit();

        List<Product> results = session.finder(Product.class)
                .between("price", 40.0, 110.0)
                .list();

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(p -> p.getPrice() >= 40.0 && p.getPrice() <= 110.0));
    }

    @Test
    void testFinderLessThan() {
        config.register(Product.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        session.save(new Product("P1", "Test", 10.0, 5, true));
        session.save(new Product("P2", "Test", 50.0, 15, true));
        session.save(new Product("P3", "Test", 100.0, 25, true));
        session.commit();

        List<Product> results = session.finder(Product.class)
                .lt("quantity", 20)
                .list();

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(p -> p.getQuantity() < 20));
    }

    @Test
    void testFinderNotEqual() {
        config.register(Product.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        session.save(new Product("P1", "Cat1", 10.0, 1, true));
        session.save(new Product("P2", "Cat2", 20.0, 1, true));
        session.save(new Product("P3", "Cat1", 30.0, 1, true));
        session.commit();

        List<Product> results = session.finder(Product.class)
                .notEq("category", "Cat1")
                .list();

        assertEquals(1, results.size());
        assertEquals("Cat2", results.get(0).getCategory());
    }

    @Test
    void testFinderComplexQuery() {
        config.register(Product.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        // Insert diverse data
        session.save(new Product("iPhone 12", "Electronics", 800.0, 50, true));
        session.save(new Product("iPhone 13", "Electronics", 900.0, 30, true));
        session.save(new Product("iPhone 14", "Electronics", 1000.0, 20, false));
        session.save(new Product("Samsung S21", "Electronics", 750.0, 40, true));
        session.save(new Product("Book A", "Books", 25.0, 100, true));
        session.save(new Product("Book B", "Books", 35.0, 80, true));
        session.commit();

        // Complex query: active electronics with price > 700, ordered by price desc, limit 2
        List<Product> results = session.finder(Product.class)
                .eq("category", "Electronics")
                .eq("active", true)
                .gt("price", 700.0)
                .orderDesc("price")
                .limit(2)
                .list();

        assertEquals(2, results.size());
        assertEquals("iPhone 13", results.get(0).getName());
        assertEquals("iPhone 12", results.get(1).getName());
    }

    // ==================== Inheritance Tests ====================

    // --- JOINED Strategy ---
    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.JOINED)
    public static class Vehicle {
        @Id(autoIncrement = true)
        Long id;
        String brand;
        @Column(columnName = "production_year")
        Integer productionYear;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Car extends Vehicle {
        Integer doors;
        String fuelType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Motorcycle extends Vehicle {
        Integer engineCC;
        Boolean hasSidecar;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SportsCar extends Car {
        Integer topSpeed;
        Boolean isConvertible;
    }

    // --- SINGLE_TABLE Strategy ---
    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    public static class Employee {
        @Id(autoIncrement = true)
        Long id;
        String name;
        Double salary;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Manager extends Employee {
        Integer teamSize;
        String department;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Developer extends Employee {
        String programmingLanguage;
        Integer experienceYears;
    }

    // --- TABLE_PER_CLASS Strategy ---
    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
    public static class Shape {
        @Id(autoIncrement = true)
        Long id;
        String color;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Circle extends Shape {
        Double radius;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Rectangle extends Shape {
        Double width;
        Double height;
    }

    // ==================== JOINED Strategy Tests ====================

    @Test
    void testFinderJoinedInheritance_FindByParentField() {
        config.register(Vehicle.class, Car.class, Motorcycle.class, SportsCar.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        // Create test data
        Car car1 = new Car();
        car1.setBrand("Toyota");
        car1.setProductionYear(2020);
        car1.setDoors(4);
        car1.setFuelType("Petrol");

        Car car2 = new Car();
        car2.setBrand("Toyota");
        car2.setProductionYear(2022);
        car2.setDoors(2);
        car2.setFuelType("Diesel");

        Car car3 = new Car();
        car3.setBrand("Honda");
        car3.setProductionYear(2021);
        car3.setDoors(4);
        car3.setFuelType("Petrol");

        session.save(car1);
        session.save(car2);
        session.save(car3);
        session.commit();

        // Find by parent field (brand)
        List<Car> toyotas = session.finder(Car.class)
                .eq("brand", "Toyota")
                .list();

        assertEquals(2, toyotas.size());
        assertTrue(toyotas.stream().allMatch(c -> "Toyota".equals(c.getBrand())));
    }

    @Test
    void testFinderJoinedInheritance_FindByChildField() {
        config.register(Vehicle.class, Car.class, Motorcycle.class, SportsCar.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        Car car1 = new Car();
        car1.setBrand("BMW");
        car1.setProductionYear(2020);
        car1.setDoors(4);
        car1.setFuelType("Petrol");

        Car car2 = new Car();
        car2.setBrand("Audi");
        car2.setProductionYear(2021);
        car2.setDoors(2);
        car2.setFuelType("Diesel");

        session.save(car1);
        session.save(car2);
        session.commit();

        // Find by child field (fuelType)
        List<Car> dieselCars = session.finder(Car.class)
                .eq("fuelType", "Diesel")
                .list();

        assertEquals(1, dieselCars.size());
        assertEquals("Audi", dieselCars.get(0).getBrand());
    }

    @Test
    void testFinderJoinedInheritance_CombineParentAndChildFields() {
        config.register(Vehicle.class, Car.class, Motorcycle.class, SportsCar.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        Car car1 = new Car();
        car1.setBrand("Toyota");
        car1.setProductionYear(2020);
        car1.setDoors(4);
        car1.setFuelType("Petrol");

        Car car2 = new Car();
        car2.setBrand("Toyota");
        car2.setProductionYear(2022);
        car2.setDoors(4);
        car2.setFuelType("Diesel");

        Car car3 = new Car();
        car3.setBrand("Honda");
        car3.setProductionYear(2021);
        car3.setDoors(4);
        car3.setFuelType("Petrol");

        session.save(car1);
        session.save(car2);
        session.save(car3);
        session.commit();

        // Combine parent field (brand) with child field (fuelType)
        List<Car> toyotaPetrol = session.finder(Car.class)
                .eq("brand", "Toyota")
                .eq("fuelType", "Petrol")
                .list();

        assertEquals(1, toyotaPetrol.size());
        assertEquals(2020, toyotaPetrol.get(0).getProductionYear());
    }

    @Test
    void testFinderJoinedInheritance_Polymorphic() {
        config.register(Vehicle.class, Car.class, Motorcycle.class, SportsCar.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        Car car = new Car();
        car.setBrand("Toyota");
        car.setProductionYear(2020);
        car.setDoors(4);
        car.setFuelType("Petrol");

        Motorcycle moto = new Motorcycle();
        moto.setBrand("Harley");
        moto.setProductionYear(2021);
        moto.setEngineCC(1200);
        moto.setHasSidecar(false);

        SportsCar sports = new SportsCar();
        sports.setBrand("Ferrari");
        sports.setProductionYear(2023);
        sports.setDoors(2);
        sports.setFuelType("Petrol");
        sports.setTopSpeed(320);
        sports.setIsConvertible(true);

        session.save(car);
        session.save(moto);
        session.save(sports);
        session.commit();

        // Query by parent class - should find all vehicles
        List<Vehicle> allVehicles = session.finder(Vehicle.class)
                .gt("productionYear", 2019)
                .list();

        assertEquals(3, allVehicles.size());

        // Query by parent class with parent field
        List<Vehicle> recentVehicles = session.finder(Vehicle.class)
                .gte("productionYear", 2021)
                .list();

        assertEquals(2, recentVehicles.size());
    }

    @Test
    void testFinderJoinedInheritance_OrderByAndLimit() {
        config.register(Vehicle.class, Car.class, Motorcycle.class, SportsCar.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        for (int i = 1; i <= 5; i++) {
            Car car = new Car();
            car.setBrand("Brand" + i);
            car.setProductionYear(2020 + i);
            car.setDoors(4);
            car.setFuelType("Petrol");
            session.save(car);
        }
        session.commit();

        // Order by year desc, limit 3
        List<Car> topCars = session.finder(Car.class)
                .orderDesc("productionYear")
                .limit(3)
                .list();

        assertEquals(3, topCars.size());
        assertEquals(2025, topCars.get(0).getProductionYear());
        assertEquals(2024, topCars.get(1).getProductionYear());
        assertEquals(2023, topCars.get(2).getProductionYear());
    }

    // ==================== SINGLE_TABLE Strategy Tests ====================

    @Test
    void testFinderSingleTable_FindByParentField() {
        config.register(Employee.class, Manager.class, Developer.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        Manager mgr1 = new Manager();
        mgr1.setName("John");
        mgr1.setSalary(80000.0);
        mgr1.setTeamSize(10);
        mgr1.setDepartment("IT");

        Manager mgr2 = new Manager();
        mgr2.setName("Jane");
        mgr2.setSalary(90000.0);
        mgr2.setTeamSize(15);
        mgr2.setDepartment("HR");

        Developer dev1 = new Developer();
        dev1.setName("Bob");
        dev1.setSalary(70000.0);
        dev1.setProgrammingLanguage("Java");
        dev1.setExperienceYears(5);

        session.save(mgr1);
        session.save(mgr2);
        session.save(dev1);
        session.commit();

        // Find managers with salary > 85000
        List<Manager> highPaidManagers = session.finder(Manager.class)
                .gt("salary", 85000.0)
                .list();

        assertEquals(1, highPaidManagers.size());
        assertEquals("Jane", highPaidManagers.get(0).getName());


        List<Employee> employees = session.finder(Employee.class).lt("salary", 81000.0).list();
        assertEquals(2, employees.size());

        for (Employee employee : employees) {
            System.out.println(employee.getClass().getSimpleName());
        }


    }

    @Test
    void testFinderSingleTable_FindByChildField() {
        config.register(Employee.class, Manager.class, Developer.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        Developer dev1 = new Developer();
        dev1.setName("Alice");
        dev1.setSalary(75000.0);
        dev1.setProgrammingLanguage("Java");
        dev1.setExperienceYears(5);

        Developer dev2 = new Developer();
        dev2.setName("Bob");
        dev2.setSalary(65000.0);
        dev2.setProgrammingLanguage("Python");
        dev2.setExperienceYears(3);

        Developer dev3 = new Developer();
        dev3.setName("Charlie");
        dev3.setSalary(80000.0);
        dev3.setProgrammingLanguage("Java");
        dev3.setExperienceYears(7);

        session.save(dev1);
        session.save(dev2);
        session.save(dev3);
        session.commit();

        // Find Java developers
        List<Developer> javaDevelopers = session.finder(Developer.class)
                .eq("programmingLanguage", "Java")
                .list();

        assertEquals(2, javaDevelopers.size());
        assertTrue(javaDevelopers.stream().allMatch(d -> "Java".equals(d.getProgrammingLanguage())));
    }

    @Test
    void testFinderSingleTable_PolymorphicQuery() {
        config.register(Employee.class, Manager.class, Developer.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        Manager mgr = new Manager();
        mgr.setName("John");
        mgr.setSalary(90000.0);
        mgr.setTeamSize(10);
        mgr.setDepartment("IT");

        Developer dev = new Developer();
        dev.setName("Alice");
        dev.setSalary(75000.0);
        dev.setProgrammingLanguage("Java");
        dev.setExperienceYears(5);

        session.save(mgr);
        session.save(dev);
        session.commit();

        // Query all employees with salary > 70000
        List<Employee> highPaidEmployees = session.finder(Employee.class)
                .gt("salary", 70000.0)
                .list();

        assertEquals(2, highPaidEmployees.size());
    }

    @Test
    void testFinderSingleTable_CombineFieldsWithOrder() {
        config.register(Employee.class, Manager.class, Developer.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        for (int i = 1; i <= 4; i++) {
            Developer dev = new Developer();
            dev.setName("Dev" + i);
            dev.setSalary(50000.0 + i * 10000);
            dev.setProgrammingLanguage(i % 2 == 0 ? "Java" : "Python");
            dev.setExperienceYears(i * 2);
            session.save(dev);
        }
        session.commit();

        // Find Java developers, ordered by salary desc
        List<Developer> javaDevelopers = session.finder(Developer.class)
                .eq("programmingLanguage", "Java")
                .orderDesc("salary")
                .list();

        assertEquals(2, javaDevelopers.size());
        assertEquals("Dev4", javaDevelopers.get(0).getName());
        assertEquals("Dev2", javaDevelopers.get(1).getName());
    }

    // ==================== TABLE_PER_CLASS Strategy Tests ====================

    @Test
    void testFinderTablePerClass_FindByParentField() {
        config.register(Shape.class, Circle.class, Rectangle.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        Circle c1 = new Circle();
        c1.setColor("Red");
        c1.setRadius(5.0);

        Circle c2 = new Circle();
        c2.setColor("Blue");
        c2.setRadius(10.0);

        Rectangle r1 = new Rectangle();
        r1.setColor("Red");
        r1.setWidth(4.0);
        r1.setHeight(6.0);

        session.save(c1);
        session.save(c2);
        session.save(r1);
        session.commit();

        // Find all red circles
        List<Circle> redCircles = session.finder(Circle.class)
                .eq("color", "Red")
                .list();

        assertEquals(1, redCircles.size());
        assertEquals(5.0, redCircles.get(0).getRadius());
    }

    @Test
    void testFinderTablePerClass_FindByChildField() {
        config.register(Shape.class, Circle.class, Rectangle.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        Circle c1 = new Circle();
        c1.setColor("Red");
        c1.setRadius(5.0);

        Circle c2 = new Circle();
        c2.setColor("Blue");
        c2.setRadius(15.0);

        Circle c3 = new Circle();
        c3.setColor("Green");
        c3.setRadius(8.0);

        session.save(c1);
        session.save(c2);
        session.save(c3);
        session.commit();

        // Find circles with radius > 7
        List<Circle> largeCircles = session.finder(Circle.class)
                .gt("radius", 7.0)
                .list();

        assertEquals(2, largeCircles.size());
    }

    @Test
    void testFinderTablePerClass_OrderAndLimit() {
        config.register(Shape.class, Circle.class, Rectangle.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        for (int i = 1; i <= 5; i++) {
            Rectangle rect = new Rectangle();
            rect.setColor("Color" + i);
            rect.setWidth(i * 2.0);
            rect.setHeight(i * 3.0);
            session.save(rect);
        }
        session.commit();

        // Order by width desc, limit 3
        List<Rectangle> topRects = session.finder(Rectangle.class)
                .orderDesc("width")
                .limit(3)
                .list();

        assertEquals(3, topRects.size());
        assertEquals(10.0, topRects.get(0).getWidth());
        assertEquals(8.0, topRects.get(1).getWidth());
        assertEquals(6.0, topRects.get(2).getWidth());
    }

    @Test
    void testFinderTablePerClass_PolymorphicQuery() {
        config.register(Shape.class, Circle.class, Rectangle.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        Circle circle = new Circle();
        circle.setColor("Red");
        circle.setRadius(5.0);

        Rectangle rect = new Rectangle();
        rect.setColor("Red");
        rect.setWidth(4.0);
        rect.setHeight(6.0);

        session.save(circle);
        session.save(rect);
        session.commit();

        // Query all red shapes (polymorphic)
        List<Shape> redShapes = session.finder(Shape.class)
                .eq("color", "Red")
                .list();

        assertEquals(2, redShapes.size());
    }

    // ==================== Deep Inheritance Tests ====================

    @Test
    void testFinderDeepInheritance_ThreeLevels() {
        config.register(Vehicle.class, Car.class, Motorcycle.class, SportsCar.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        SportsCar sc1 = new SportsCar();
        sc1.setBrand("Ferrari");
        sc1.setProductionYear(2023);
        sc1.setDoors(2);
        sc1.setFuelType("Petrol");
        sc1.setTopSpeed(320);
        sc1.setIsConvertible(true);

        SportsCar sc2 = new SportsCar();
        sc2.setBrand("Lamborghini");
        sc2.setProductionYear(2022);
        sc2.setDoors(2);
        sc2.setFuelType("Petrol");
        sc2.setTopSpeed(350);
        sc2.setIsConvertible(false);

        SportsCar sc3 = new SportsCar();
        sc3.setBrand("Porsche");
        sc3.setProductionYear(2024);
        sc3.setDoors(2);
        sc3.setFuelType("Hybrid");
        sc3.setTopSpeed(300);
        sc3.setIsConvertible(true);

        session.save(sc1);
        session.save(sc2);
        session.save(sc3);
        session.commit();

        // Query combining fields from all 3 levels:
        // - Vehicle: brand, year
        // - Car: fuelType
        // - SportsCar: isConvertible
        List<SportsCar> convertiblePetrol = session.finder(SportsCar.class)
                .eq("fuelType", "Petrol")
                .eq("isConvertible", true)
                .orderDesc("topSpeed")
                .list();

        assertEquals(1, convertiblePetrol.size());
        assertEquals("Ferrari", convertiblePetrol.get(0).getBrand());
    }

    @Test
    void testFinderDeepInheritance_QueryFromMiddleClass() {
        config.register(Vehicle.class, Car.class, Motorcycle.class, SportsCar.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();
        session.begin();

        Car normalCar = new Car();
        normalCar.setBrand("Toyota");
        normalCar.setProductionYear(2020);
        normalCar.setDoors(4);
        normalCar.setFuelType("Petrol");

        SportsCar sportsCar = new SportsCar();
        sportsCar.setBrand("Ferrari");
        sportsCar.setProductionYear(2023);
        sportsCar.setDoors(2);
        sportsCar.setFuelType("Petrol");
        sportsCar.setTopSpeed(320);
        sportsCar.setIsConvertible(true);

        session.save(normalCar);
        session.save(sportsCar);
        session.commit();

        // Query from Car class - should include SportsCar (polymorphic)
        List<Car> petrolCars = session.finder(Car.class)
                .eq("fuelType", "Petrol")
                .list();

        assertEquals(2, petrolCars.size());
    }
}
