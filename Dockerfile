FROM openjdk:17
EXPOSE 8080
ADD target/dbdemo-0.0.2-SNAPSHOT.jar dbdemo-0.0.2-SNAPSHOT.jar
ENTRYPOINT ["java","-jar","/dbdemo-0.0.2-SNAPSHOT.jar"]
