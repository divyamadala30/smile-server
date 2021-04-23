FROM maven:3.6.1-jdk-8-slim
RUN mkdir /cmo-metadb

ADD . /cmo-metadb
WORKDIR /cmo-metadb
RUN mvn clean install

FROM openjdk:8-slim
WORKDIR /cmo-metadb
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} cmo_metadb_server.jar
#COPY --from=0 /server/terget/cmo_metadb_server.jar /cmo-metadb
CMD ["java -jar /cmo_metadb_server.jar"]
ENTRYPOINT ["java","-jar","/cmo_metadb_server.jar"]
