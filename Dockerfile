FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN mvn --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home crawlforge \
    && mkdir -p /app/data \
    && chown -R crawlforge:crawlforge /app
WORKDIR /app
COPY --from=build /workspace/target/crawlforge-0.0.1-SNAPSHOT.jar app.jar
USER crawlforge
ENV SPRING_DATASOURCE_URL="jdbc:h2:file:/app/data/crawlforge;MODE=PostgreSQL"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
