# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom first to leverage Docker layer caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw -q -DskipTests dependency:go-offline || true

# Copy source and build
COPY src src
RUN ./mvnw -DskipTests clean package

# ---- Run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy jar from build stage (adjust jar name pattern if needed)
COPY --from=build /app/target/*.jar app.jar

# Render sets PORT; Spring must bind to it
ENV PORT=10000
EXPOSE 10000

# Recommended JVM settings for containers
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=$PORT -jar app.jar"]
