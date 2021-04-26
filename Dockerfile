FROM maven:3.6.1-jdk-8-slim
RUN mkdir /cmo-metadb

ADD . /cmo-metadb
WORKDIR /cmo-metadb
RUN mvn clean install

FROM openjdk:8-slim
WORKDIR /cmo-metadb
COPY server/target/cmo_metadb_server.jar cmo_metadb_server.jar
CMD ["java -jar cmo_metadb_server.jar"]
ENTRYPOINT ["java","-jar","cmo_metadb_server.jar"]
