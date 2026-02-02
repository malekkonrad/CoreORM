package pl.edu.agh.dp;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.edu.agh.dp.api.Configuration;
import pl.edu.agh.dp.api.Orm;
import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.api.SessionFactory;
import pl.edu.agh.dp.api.annotations.*;
import pl.edu.agh.dp.core.mapping.InheritanceType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ComplexTest {
    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.JOINED)
    public static class AnimalOwner {
        @Id(autoIncrement = true)
        Long id2;
        String name;
        @ManyToMany
        List<Animal> animals;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BadAnimalOwner extends AnimalOwner {
        String surname;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
    public static class Animal {
        @Id(autoIncrement = true)
        Long id;
        String name;
        @ManyToMany
        List<AnimalOwner> owner;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Cat extends Animal {
        private String catName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Dog extends Animal {
        Integer age;
        String color;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Version {
        @Id(autoIncrement = true)
        Long id;
        String name;
        @OneToMany
        @JoinColumn(joinColumns = { "assignment" })
        List<Assignment> assignment;
        @OneToMany
        @JoinColumn(joinColumns = { "blocks" })
        List<Block> blocks;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Assignment {
        @Id(autoIncrement = true)
        Long id;
        String name;
        @ManyToOne
        @JoinColumn(joinColumns = { "version" })
        Version version;
        @OneToMany
        @JoinColumn(joinColumns = { "blocks", "version" })
        List<Block> blocks;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Block {
        @Id(autoIncrement = true)
        Long id;
        String name;
        @ManyToOne
        @JoinColumn(joinColumns = { "version" })
        Version version;
        @ManyToOne
        @JoinColumn(joinColumns = { "assignment", "version" })
        Assignment assignment;
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
    public void versionTest() {
        config.register(Version.class, Assignment.class, Block.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        {
            Version version = new Version();
            version.name = "v1";

            Version version2 = new Version();
            version2.name = "v2";

            Assignment assignment = new Assignment();
            assignment.name = "ass1";

            Block block = new Block();
            block.name = "b1";

            Block block2 = new Block();
            block2.name = "b2";

            version.blocks = new ArrayList<>();
            version2.blocks = new ArrayList<>();
            version.assignment = new ArrayList<>();
            version2.assignment = new ArrayList<>();

            session.save(version);
            session.save(version2);

            assignment.version = version;
            assignment.blocks = new ArrayList<>(){{
                add(block);add(block2);
            }};
            block.version = version;
            block2.version = version2;

            session.save(assignment);
            session.commit();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            Assignment assignment = session.find(Assignment.class, 1L);
            assignment.blocks.size();
            assertEquals(1, assignment.blocks.size());
            assertNotNull(assignment.blocks.get(0).assignment);
        }
    }

    @Test
    public void complexTest() {
        config.register(AnimalOwner.class, Animal.class, Cat.class, Dog.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            Dog dog = new Dog();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            Cat cat = new Cat();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            AnimalOwner owner = new AnimalOwner();
            owner.setName("John");
            owner.setAnimals(new ArrayList<>() {{
                add(dog);
                add(cat);
            }});

            session.save(owner);
            session.commit();

            ownerId = owner.getId2();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalOwner owner = session.find(AnimalOwner.class, ownerId);
            owner.animals.size();
            System.out.println(owner.animals);
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            Animal animal = session.find(Animal.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }

    @Test
    public void doubleInheritanceTest() {
        config.register(AnimalOwner.class, BadAnimalOwner.class, Animal.class, Cat.class, Dog.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            Dog dog = new Dog();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            Cat cat = new Cat();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            BadAnimalOwner owner = new BadAnimalOwner();
            owner.setName("John");
            owner.setSurname("Smith");
            owner.setAnimals(new ArrayList<>() {{
                add(dog);
                add(cat);
            }});

            session.save(owner);
            session.commit();

            ownerId = owner.getId2();
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalOwner owner = session.find(AnimalOwner.class, ownerId);
            owner.animals.size();
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            Animal animal = session.find(Animal.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }
}
