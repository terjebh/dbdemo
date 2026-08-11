# Bygg: mvnw clean package (eller Jenkins)
# Kjør: java -jar target/dbdemo-*.jar
# Java 25 matcher pom.xml (java.version=25)
FROM eclipse-temurin:25-jre

WORKDIR /app

# Kopier den bygde jar-en (bygg først med: ./mvnw clean package)
ARG JAR_FILE=target/dbdemo-*.jar
COPY ${JAR_FILE} dbdemo.jar

# Entrypoint: chowner volume-mapper til appuser ved start, så vertens
# eierskap aldri blokkerer skriving (docker run -v ./data:... fungerer
# uten manuell chown på verten). Appen kjører fortsatt som appuser.
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Fast UID 1001 (appuser) — konsistent med volume-eierskap
RUN useradd --create-home --shell /bin/bash -u 1001 appuser \
    && mkdir -p /app/logs /home/appuser/.dbdemo \
    && chown -R appuser:appuser /app /home/appuser

# Kjører som root kun i entrypoint (for chown), dropper deretter til appuser
USER root

EXPOSE 8080
ENTRYPOINT ["/entrypoint.sh"]
