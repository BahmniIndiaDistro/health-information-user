FROM adoptopenjdk/openjdk11:jre-11.0.20_8
VOLUME /tmp
COPY build/libs/*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"] 
