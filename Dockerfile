# Bygg: mvnw clean package (eller Jenkins)
# Kjør: java -jar target/dbdemo-*.jar
# Java 17 matcher pom.xml (maven.compiler.source/target)
FROM eclipse-temurin:17-jre

WORKDIR /app

# Kopier den bygde jar-en (bygg først med: ./mvnw clean package)
ARG JAR_FILE=target/dbdemo-*.jar
COPY ${JAR_FILE} dbdemo.jar

# Kjør som ikke-root-bruker
RUN useradd --create-home --shell /bin/bash appuser \
    && mkdir -p /app/logs && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/dbdemo.jar"]
