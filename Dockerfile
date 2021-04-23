FROM maven:3.6.1-jdk-8-slim
RUN mkdir /cmo-metadb

ADD . /cmo-metadb
WORKDIR /cmo-metadb
RUN mvn -Dspring.config.location=/conf/cmo-metadb/application.properties clean install

FROM openjdk:8-slim
COPY --from=0 /server/target/cmo-metadb.jar /cmo-metadb.jar
CMD ["java", "${JAVA_OPTS}", "-jar", "/cmo-metadb.jar"]
#ENTRYPOINT ["sh", "-c", "java -jar /cmo-metadb.jar"]
