# ---- Build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache dependencies by copying POMs first.
COPY pom.xml .
COPY esb-common/pom.xml esb-common/
COPY esb-core/pom.xml esb-core/
COPY esb-transport/pom.xml esb-transport/
COPY esb-transform/pom.xml esb-transform/
COPY esb-governance/pom.xml esb-governance/
COPY esb-admin/pom.xml esb-admin/
COPY esb-app/pom.xml esb-app/
RUN mvn -B -q -DskipTests dependency:go-offline

COPY . .
RUN mvn -B -DskipTests clean package

# ---- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime
RUN addgroup -S esb && adduser -S esb -G esb
WORKDIR /app
COPY --from=build /build/esb-app/target/king-esb-platform.jar app.jar
USER esb
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
