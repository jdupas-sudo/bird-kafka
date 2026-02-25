FROM maven:3.9-eclipse-temurin-17

WORKDIR /gatling

COPY pom.xml .
RUN mvn -B -DskipTests \
  org.apache.maven.plugins:maven-dependency-plugin:3.7.0:resolve \
  org.apache.maven.plugins:maven-dependency-plugin:3.7.0:resolve-plugins

COPY src/ src/
RUN mvn -B -DskipTests test-compile

ENTRYPOINT ["mvn", "gatling:test"]
