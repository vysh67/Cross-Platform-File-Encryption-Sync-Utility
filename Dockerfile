FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/target/secure-file-sync-1.0.0.jar app.jar

RUN mkdir -p /data/source /data/backup /data/db

EXPOSE 8080

ENTRYPOINT [
  "java",
  "-jar",
  "app.jar",
  "daemon",
  "--source","/data/source",
  "--backup","/data/backup",
  "--db","/data/db/secure-sync.db",
  "--password-env","SECURE_SYNC_PASSWORD",
  "--interval-seconds","60",
  "--api-port","8080"
]