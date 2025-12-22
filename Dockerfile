## Build stage
#FROM maven:3.9-eclipse-temurin-17 AS build
#WORKDIR /app
#
#COPY pom.xml .
#
## 2. Pre-download dependencies (cacheable layer)
#RUN mvn dependency:go-offline -B
#
## This is faster because the src change many times,
## but the dependencies almost never, so they are kept in cache
## 3. Copy the actual source code
#COPY src ./src
#
## 4. Build the project — now it's fast because deps are cached
#RUN mvn clean package dependency:copy-dependencies -DskipTests
#
## Run stage
#FROM eclipse-temurin:17-jre
#WORKDIR /app
#
## kopiujemy aplikację
#COPY --from=build /app/target/CoreORM-1.0-SNAPSHOT.jar app.jar
## kopiujemy wszystkie zależności (w tym org.postgresql)
#COPY --from=build /app/target/dependency ./lib
#
## odpalamy z classpathem: app.jar + wszystkie lib
#CMD ["java", "-cp", "app.jar:lib/*", "pl.edu.agh.dp.App"]
#
#


# Używamy obrazu z Mavenem
FROM maven:3.9-eclipse-temurin-17
WORKDIR /app

# Kopiujemy pom.xml i pobieramy zależności (cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Kopiujemy kod źródłowy
COPY src ./src

# Domyślna komenda to uruchomienie testów
CMD ["mvn", "test"]
