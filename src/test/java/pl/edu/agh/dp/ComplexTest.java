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
    @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
    public static class AnimalOwnerT2T {
        @Id(autoIncrement = true)
        Long id2;
        String name;
        @ManyToMany
        List<AnimalT2T> animals;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.JOINED)
    public static class AnimalOwnerJ2J {
        @Id(autoIncrement = true)
        Long id2;
        String name;
        @ManyToMany
        List<AnimalJ2J> animals;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    public static class AnimalOwnerS2S {
        @Id(autoIncrement = true)
        Long id2;
        String name;
        @ManyToMany
        List<AnimalS2S> animals;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BadAnimalOwnerT2T extends AnimalOwnerT2T {
        String surname;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BadAnimalOwnerJ2J extends AnimalOwnerJ2J {
        String surname;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BadAnimalOwnerS2S extends AnimalOwnerS2S {
        String surname;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
    public static class AnimalT2T {
        @Id(autoIncrement = true)
        Long id;
        String name;
        @ManyToMany
        List<AnimalOwnerT2T> owner;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CatT2T extends AnimalT2T {
        private String catName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DogT2T extends AnimalT2T {
        Integer age;
        String color;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.JOINED)
    public static class AnimalJ2J {
        @Id(autoIncrement = true)
        Long id;
        String name;
        @ManyToMany
        List<AnimalOwnerJ2J> owner;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CatJ2J extends AnimalJ2J {
        private String catName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DogJ2J extends AnimalJ2J {
        Integer age;
        String color;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    public static class AnimalS2S {
        @Id(autoIncrement = true)
        Long id;
        String name;
        @ManyToMany
        List<AnimalOwnerS2S> owner;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CatS2S extends AnimalS2S {
        private String catName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DogS2S extends AnimalS2S {
        Integer age;
        String color;
    }
//  --------------- T2J ----------------
    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
    public static class AnimalOwnerT2J {
        @Id(autoIncrement = true)
        Long id2;
        String name;
        @ManyToMany
        List<AnimalT2J> animals;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BadAnimalOwnerT2J extends AnimalOwnerT2J {
        String surname;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.JOINED)
    public static class AnimalT2J {
        @Id(autoIncrement = true)
        Long id;
        String name;
        @ManyToMany
        List<AnimalOwnerT2J> owner;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CatT2J extends AnimalT2J {
        private String catName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DogT2J extends AnimalT2J {
        Integer age;
        String color;
    }
//  ------------------------------------

//  --------------- T2S ----------------
    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
    public static class AnimalOwnerT2S {
        @Id(autoIncrement = true)
        Long id2;
        String name;
        @ManyToMany
        List<AnimalT2S> animals;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BadAnimalOwnerT2S extends AnimalOwnerT2S {
        String surname;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    public static class AnimalT2S {
        @Id(autoIncrement = true)
        Long id;
        String name;
        @ManyToMany
        List<AnimalOwnerT2S> owner;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CatT2S extends AnimalT2S {
        private String catName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DogT2S extends AnimalT2S {
        Integer age;
        String color;
    }
//  ------------------------------------

//  --------------- J2S ----------------
    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.SINGLE_TABLE)
    public static class AnimalOwnerJ2S {
        @Id(autoIncrement = true)
        Long id2;
        String name;
        @ManyToMany
        List<AnimalJ2S> animals;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BadAnimalOwnerJ2S extends AnimalOwnerJ2S {
        String surname;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Inheritance(strategy = InheritanceType.JOINED)
    public static class AnimalJ2S {
        @Id(autoIncrement = true)
        Long id;
        String name;
        @ManyToMany
        List<AnimalOwnerJ2S> owner;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CatJ2S extends AnimalJ2S {
        private String catName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DogJ2S extends AnimalJ2S {
        Integer age;
        String color;
    }
//  ------------------------------------

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
        Version version; // -> version_id_fkey
        @OneToMany
        @JoinColumn(joinColumns = { "blocks", "version" })
        List<Block> blocks; // -> join (
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
    public void tablePerClass2TablePerClassTest() {
        config.register(AnimalOwnerT2T.class, AnimalT2T.class, CatT2T.class, DogT2T.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            DogT2T dog = new DogT2T();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            CatT2T cat = new CatT2T();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            AnimalOwnerT2T owner = new AnimalOwnerT2T();
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
            AnimalOwnerT2T owner = session.find(AnimalOwnerT2T.class, ownerId);
            owner.animals.size();
            System.out.println(owner.animals);
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalT2T animal = session.find(AnimalT2T.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }

    @Test
    public void tablePerClass2TablePerClassDoubleInheritanceTest() {
        config.register(AnimalOwnerT2T.class, BadAnimalOwnerT2T.class, AnimalT2T.class, CatT2T.class, DogT2T.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            DogT2T dog = new DogT2T();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            CatT2T cat = new CatT2T();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            BadAnimalOwnerT2T owner = new BadAnimalOwnerT2T();
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
            AnimalOwnerT2T owner = session.find(AnimalOwnerT2T.class, ownerId);
            owner.animals.size();
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalT2T animal = session.find(AnimalT2T.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }

    @Test
    public void joined2JoinedTest() {
        config.register(AnimalOwnerJ2J.class, AnimalJ2J.class, CatJ2J.class, DogJ2J.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            DogJ2J dog = new DogJ2J();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            CatJ2J cat = new CatJ2J();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            AnimalOwnerJ2J owner = new AnimalOwnerJ2J();
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
            AnimalOwnerJ2J owner = session.find(AnimalOwnerJ2J.class, ownerId);
            owner.animals.size();
            System.out.println(owner.animals);
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalJ2J animal = session.find(AnimalJ2J.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }

    @Test
    public void joined2JoinedDoubleInheritanceTest() {
        config.register(AnimalOwnerJ2J.class, BadAnimalOwnerJ2J.class, AnimalJ2J.class, CatJ2J.class, DogJ2J.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            DogJ2J dog = new DogJ2J();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            CatJ2J cat = new CatJ2J();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            BadAnimalOwnerJ2J owner = new BadAnimalOwnerJ2J();
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
            AnimalOwnerJ2J owner = session.find(AnimalOwnerJ2J.class, ownerId);
            owner.animals.size();
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalJ2J animal = session.find(AnimalJ2J.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }

    @Test
    public void single2SingleTest() {
        config.register(AnimalOwnerS2S.class, AnimalS2S.class, CatS2S.class, DogS2S.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            DogS2S dog = new DogS2S();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            CatS2S cat = new CatS2S();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            AnimalOwnerS2S owner = new AnimalOwnerS2S();
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
            AnimalOwnerS2S owner = session.find(AnimalOwnerS2S.class, ownerId);
            owner.animals.size();
            System.out.println(owner.animals);
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalS2S animal = session.find(AnimalS2S.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }

    @Test
    public void single2SingleDoubleInheritanceTest() {
        config.register(AnimalOwnerS2S.class, BadAnimalOwnerS2S.class, AnimalS2S.class, CatS2S.class, DogS2S.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            DogS2S dog = new DogS2S();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            CatS2S cat = new CatS2S();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            BadAnimalOwnerS2S owner = new BadAnimalOwnerS2S();
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
            AnimalOwnerS2S owner = session.find(AnimalOwnerS2S.class, ownerId);
            owner.animals.size();
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalS2S animal = session.find(AnimalS2S.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }

    @Test
    public void table2JoinedClassTest() {
        config.register(AnimalOwnerT2J.class, AnimalT2J.class, CatT2J.class, DogT2J.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            DogT2J dog = new DogT2J();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            CatT2J cat = new CatT2J();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            AnimalOwnerT2J owner = new AnimalOwnerT2J();
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
            AnimalOwnerT2J owner = session.find(AnimalOwnerT2J.class, ownerId);
            owner.animals.size();
            System.out.println(owner.animals);
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalT2J animal = session.find(AnimalT2J.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }

    @Test
    public void table2JoinedClassTestDoubleInheritanceTest() {
        config.register(AnimalOwnerT2J.class, BadAnimalOwnerT2J.class, AnimalT2J.class, CatT2J.class, DogT2J.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            DogT2J dog = new DogT2J();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            CatT2J cat = new CatT2J();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            BadAnimalOwnerT2J owner = new BadAnimalOwnerT2J();
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
            AnimalOwnerT2J owner = session.find(AnimalOwnerT2J.class, ownerId);
            owner.animals.size();
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalT2J animal = session.find(AnimalT2J.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }

    @Test
    public void table2SingleClassTest() {
        config.register(AnimalOwnerT2S.class, AnimalT2S.class, CatT2S.class, DogT2S.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            DogT2S dog = new DogT2S();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            CatT2S cat = new CatT2S();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            AnimalOwnerT2S owner = new AnimalOwnerT2S();
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
            AnimalOwnerT2S owner = session.find(AnimalOwnerT2S.class, ownerId);
            owner.animals.size();
            System.out.println(owner.animals);
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalT2S animal = session.find(AnimalT2S.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }

    @Test
    public void table2SingleClassTestDoubleInheritanceTest() {
        config.register(AnimalOwnerT2S.class, BadAnimalOwnerT2S.class, AnimalT2S.class, CatT2S.class, DogT2S.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            DogT2S dog = new DogT2S();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            CatT2S cat = new CatT2S();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            BadAnimalOwnerT2S owner = new BadAnimalOwnerT2S();
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
            AnimalOwnerT2S owner = session.find(AnimalOwnerT2S.class, ownerId);
            owner.animals.size();
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalT2S animal = session.find(AnimalT2S.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }

    @Test
    public void joined2SingleClassTest() {
        config.register(AnimalOwnerJ2S.class, AnimalJ2S.class, CatJ2S.class, DogJ2S.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            DogJ2S dog = new DogJ2S();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            CatJ2S cat = new CatJ2S();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            AnimalOwnerJ2S owner = new AnimalOwnerJ2S();
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
            AnimalOwnerJ2S owner = session.find(AnimalOwnerJ2S.class, ownerId);
            owner.animals.size();
            System.out.println(owner.animals);
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalJ2S animal = session.find(AnimalJ2S.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }

    @Test
    public void joined2SingleClassTestDoubleInheritanceTest() {
        config.register(AnimalOwnerJ2S.class, BadAnimalOwnerJ2S.class, AnimalJ2S.class, CatJ2S.class, DogJ2S.class);
        sessionFactory = config.buildSessionFactory();
        session = sessionFactory.openSession();

        Long ownerId;

        {
            DogJ2S dog = new DogJ2S();
            dog.setName("Dog1");
            dog.setColor("black");
            dog.setAge(10);
            dog.setOwner(new ArrayList<>());

            CatJ2S cat = new CatJ2S();
            cat.setCatName("Asteroid destroyer");
            cat.setName("Cat1");
            cat.setOwner(new ArrayList<>());

            BadAnimalOwnerJ2S owner = new BadAnimalOwnerJ2S();
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
            AnimalOwnerJ2S owner = session.find(AnimalOwnerJ2S.class, ownerId);
            owner.animals.size();
            assertEquals(2, owner.animals.size());
        }

        session.close();
        session = sessionFactory.openSession();

        {
            AnimalJ2S animal = session.find(AnimalJ2S.class, 1L);
            session.load(animal, "owner");
            assertNotNull(animal.owner);
        }
    }
}
