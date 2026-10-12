# ==============================================================================
# Dockerfile Multi-Etapa para Bunnycure Backend (Spring Boot 3.2 / Java 21)
# Diseñado para entornos de Staging y Producción
# ==============================================================================

# --- Etapa 1: Compilación y empaquetado (Build Stage) ---
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Instalar bash y herramientas básicas para el wrapper
RUN apk add --no-cache bash

# Copiar configuración de dependencias para aprovechar la caché de Docker
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Copiar el código fuente y compilar artefacto JAR ejecutable
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# --- Etapa 2: Entorno de ejecución ligero (Runtime Stage) ---
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Crear usuario y grupo sin privilegios de root por seguridad
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiar el JAR generado desde la etapa de construcción
COPY --from=builder /workspace/target/*.jar app.jar

# Variables de entorno por defecto para contenedores
ENV SERVER_PORT=8080 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

# Chequeo de salud mediante el endpoint Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
