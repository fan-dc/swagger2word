FROM anapsix/alpine-java:8u201b09_jdk

COPY target/Swagger2Word-exec.jar /work/Swagger2Word.jar

EXPOSE 8080

WORKDIR /work

ENTRYPOINT ["java", "-jar", "Swagger2Word.jar"]
