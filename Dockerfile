# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Tests and coverage are enforced in CI; the image build stays fast and deterministic.
RUN mvn -B -q package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system learnium && useradd --system --gid learnium learnium
COPY --from=build /app/target/*.jar app.jar
USER learnium
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
