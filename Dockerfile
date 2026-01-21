# Multi-stage build for combined backend (Spring Boot) and frontend (Vue/Vite)

ARG VERSION=dev

##### Backend build stage #####
FROM maven:3.9-eclipse-temurin-21 AS backend-builder

ARG VERSION
WORKDIR /build

# Copy parent pom
COPY pom.xml /build/parent-pom.xml

# Copy backend module pom and sources
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src

# Fix relativePath in backend pom to point to copied parent pom
RUN sed -i 's|<relativePath>../pom.xml</relativePath>|<relativePath>parent-pom.xml</relativePath>|' pom.xml

# Build backend jar (skip tests)
RUN mvn clean package -DskipTests


##### Frontend build stage #####
FROM node:22-alpine AS frontend-builder

ARG VERSION
ENV VITE_APP_VERSION=${VERSION}

WORKDIR /build

# Copy root-level package.json / lockfile for frontend (the Docker build context is repo root)
COPY frontend/package.json frontend/yarn.lock ./

# Install dependencies
RUN yarn install

# Copy frontend source code
COPY frontend/ ./

# Build production assets
RUN yarn build-only


##### Final runtime image (backend + nginx) #####
FROM eclipse-temurin:21-jre-alpine

ARG VERSION
WORKDIR /app

# Install nginx and busybox-extras (for nc in healthcheck)
RUN apk add --no-cache nginx busybox-extras

# Copy backend jar
COPY --from=backend-builder /build/target/*.jar /app/app.jar

# Copy frontend static files into nginx html root
COPY --from=frontend-builder /build/dist /usr/share/nginx/html

# Copy nginx configuration from frontend module
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

# Create backend-related directories
RUN mkdir -p /app/logs /app/drivers /app/config /app/job

# Expose backend and frontend ports
EXPOSE 50601 80

# Simple healthcheck: check backend port and nginx HTTP
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD nc -z localhost 50601 && wget --quiet --tries=1 --spider http://localhost/ || exit 1

# Start both backend (Spring Boot) and nginx
# Use a simple shell script as entrypoint to run both processes
CMD ["sh", "-c", "\
  java \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dloader.path=/app/drivers \
    -Dloader.main=com.wgzhao.addax.admin.AdminApplication \
    -cp app.jar \
    org.springframework.boot.loader.launch.PropertiesLauncher \
  & \
  nginx -g 'daemon off;' \
"]
