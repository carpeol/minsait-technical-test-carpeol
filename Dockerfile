FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src src

RUN mvn -pl src/boot/similar-product-finder-service -am package -DskipTests

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /workspace/src/boot/similar-product-finder-service/target/similar-product-finder-service-*.jar app.jar

EXPOSE 5000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
