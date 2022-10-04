FROM openjdk:17-alpine
MAINTAINER Desmond Kimutai <desmondkym.dk@gmail.com>
ARG JAR_FILE=target/*.jar
COPY $JAR_FILE app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
EXPOSE 9000:9000