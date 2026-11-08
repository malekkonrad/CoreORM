# CoreORM

![Java 17](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-build-C71A36?logo=apachemaven&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql&logoColor=white)
![JUnit 5](https://img.shields.io/badge/JUnit-5-25A162?logo=junit5&logoColor=white)

**A Hibernate-inspired ORM for Java, written from scratch on top of plain JDBC.**

CoreORM maps annotated Java classes to relational tables using reflection. It generates the
schema, persists and loads object graphs with relationships, loads collections lazily, runs
work in transactions and offers a fluent query API. It supports the three classic
inheritance strategies plus a custom hybrid one.

It was built as a team project for the *Design Patterns* course at AGH University of Krakow.

---

## Features

- **Annotation-based mapping**: entities are registered explicitly or found by `scanPackages(...)`; `@Id`, `@Column` (nullable, unique, index, length, precision, default value), `@Table`.
- **Relationships**: `@OneToOne`, `@OneToMany`, `@ManyToOne`, `@ManyToMany` (with join tables), including self-references and composite keys.
- **Lazy loading**: collections are replaced by `LazyList` / `LazySet` proxies that query the database on first access. `session.load(entity, "relation")` loads a relation explicitly.
- **Sessions and transactions**: `begin` / `commit` / `rollback` / `flush`. `Session` is `AutoCloseable`.
- **Fluent query API (`Finder`)**: `eq`, `notEq`, `like`, `gt/gte/lt/lte`, `in`, `between`, `isNull`, sorting, `limit` / `offset`, `fetch` joins, plus `list()`, `first()` and `single()`.
- **Schema management**: `orm.schema.auto` = `create` / `none`, with a schema generator, validator and dropper.
- **Four inheritance strategies**:

| Strategy | Tables | Notes |
|---|---|---|
| `SINGLE_TABLE` | one table for the whole hierarchy | discriminator column (`@DiscriminatorColumn`, `@DiscriminatorValue`) |
| `JOINED` | one table per class | subclass tables hold their own fields plus an FK to the parent |
| `TABLE_PER_CLASS` | one table per concrete class | inherited fields are copied into every table |
| `CONCRETE_CLASS` *(custom)* | hybrid | an abstract parent is merged into the child table; a concrete parent is joined by FK |

## Quick look

```java
@Inheritance(strategy = InheritanceType.JOINED)
public class Account {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    private BigDecimal balance;
}

public class Employee {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String lastName;

    @OneToMany(mappedBy = "manager")
    @JoinColumn(joinColumns = {"subordinates"})
    private List<Employee> subordinates = new ArrayList<>();   // loaded lazily

    @ManyToOne(mappedBy = "subordinates")
    @JoinColumn(joinColumns = {"manager"}, nullable = true)
    private Employee manager;
}
```

```java
SessionFactory factory = Orm.configure()
        .setProperty("db.url", "jdbc:postgresql://localhost:5432/orm_demo")
        .setProperty("db.user", "orm_user")
        .setProperty("db.password", "secret")
        .setProperty("orm.schema.auto", "create")
        .register(Employee.class, Department.class, Account.class)
        .buildSessionFactory();

try (Session session = factory.openSession()) {
    session.save(employee);
    session.commit();

    List<Employee> seniors = session.finder(Employee.class)
            .gte("salary", 10_000)
            .like("lastName", "Smi%")
            .orderDesc("hireDate")
            .limit(10)
            .list();
}
```

## Architecture

```
pl.edu.agh.dp.core
├── api/          public API: Orm, Configuration, SessionFactory, Session, lazy collections
├── mapping/      annotations, reflection-based metadata (EntityMetadata, AssociationMetadata, ...)
├── persister/    EntityPersister and the inheritance strategies (Strategy pattern)
├── finder/       Finder, QuerySpec and the condition types (query object)
├── jdbc/         ConnectionProvider, JdbcExecutor, SQL dialect
├── schema/       schema generation, validation and dropping
└── util/         reflection and string helpers
```

Design patterns we used, documented in [`docs/Design_Patterns.pdf`](docs/Design_Patterns.pdf) with package and class diagrams:

- **Data Mapper**: entities stay plain objects, and persisters move data between objects and tables.
- **Strategy**: each inheritance mapping is a separate `InheritanceStrategy`, chosen per hierarchy.
- **Factory Method**: `InheritanceStrategyFactory` creates the right strategy from the entity metadata.
- **Lazy-loading proxies**: `LazyList` / `LazySet` stand in for collections until they are first used.

## Running the tests

The test suite (JUnit 5) covers basic CRUD, relationships, composite keys, every inheritance
strategy, the `Finder` API and an example domain (employees, departments, accounts,
notifications).

```bash
# Local run on an in-memory H2 database in PostgreSQL mode
mvn test

# Against a real PostgreSQL in Docker
docker compose up --build
```

To look inside the database while the containers are running:

```bash
docker compose exec db psql -U orm_user -d orm_demo
# \dt lists tables, \q quits
```

A sample Spring Boot REST app that uses the library lives in
[CoreOrm-Usage](https://github.com/malekkonrad/CoreOrm-Usage).

## Authors

- **Konrad Małek** ([@malekkonrad](https://github.com/malekkonrad))
- **Mateusz Kotarba**
- **Bartłomiej Mazgaj**

AGH University of Krakow, Computer Science & Intelligent Systems
