# ── Stage 1: Build Stage ──────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy Maven POM descriptor to leverage Docker layer caching
COPY pom.xml .

# Download dependencies (fail-safe offline caching)
RUN mvn dependency:go-offline -B

# Copy application source code
COPY src ./src

# Build executable jar, skipping unit tests during image build
RUN mvn clean package -DskipTests

# ── Stage 2: Production Runtime Stage ─────────────────────────
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Run as non-root user for security compliance
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

# Copy built artifact from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Expose default HTTP port
EXPOSE 8080

# Configure container memory limits and execution command
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
