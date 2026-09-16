# Stage 1: Build using Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy parent and module definitions
COPY pom.xml .
COPY common-dto/pom.xml common-dto/
COPY payment-service/pom.xml payment-service/
COPY ledger-service/pom.xml ledger-service/

# Cache dependencies
RUN mvn -B dependency:go-offline -pl common-dto,payment-service

# Copy sources and compile
COPY common-dto/src common-dto/src
COPY payment-service/src payment-service/
RUN mvn clean package -DskipTests -pl common-dto,payment-service

# Stage 2: Minimal Runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/payment-service/target/payment-service-1.0.0.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
