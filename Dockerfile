FROM openjdk:8-jdk-alpine
RUN apk add --no-cache curl
COPY target/git-spoon-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
