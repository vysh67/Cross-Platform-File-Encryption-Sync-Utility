FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/target/secure-file-sync-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s \
CMD wget -qO- http://localhost:8080/health || exit 1