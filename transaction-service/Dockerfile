FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY fraud-service/pom.xml fraud-service/
COPY transaction-service/pom.xml transaction-service/
RUN mvn -q -pl transaction-service -am dependency:go-offline
COPY transaction-service transaction-service
RUN mvn -q -pl transaction-service -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/transaction-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
