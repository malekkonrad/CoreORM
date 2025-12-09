# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

# budujemy jar + kopiujemy zależności do target/dependency
RUN mvn clean package dependency:copy-dependencies -DskipTests

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# kopiujemy aplikację
COPY --from=build /app/target/CoreORM-1.0-SNAPSHOT.jar app.jar
# kopiujemy wszystkie zależności (w tym org.postgresql)
COPY --from=build /app/target/dependency ./lib

# odpalamy z classpathem: app.jar + wszystkie lib
CMD ["java", "-cp", "app.jar:lib/*", "pl.edu.agh.dp.App"]
