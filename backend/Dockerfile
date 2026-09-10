# Etapa 1: compilar el .jar con Maven (Java 21)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# Etapa 2: imagen JRE mínima que solo ejecuta el .jar
FROM eclipse-temurin:21-jre
WORKDIR /app
# curl lo usa el health check de ECS
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
