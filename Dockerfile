FROM adoptopenjdk/maven-openjdk8 AS builder

COPY pom.xml .
COPY src src
RUN mvn clean package dependency:copy-dependencies

FROM adoptopenjdk/openjdk8 AS runtime

WORKDIR /app

COPY --from=builder /target/pipe-1.2.0.jar /app/pipe-1.2.0.jar
COPY --from=builder /target/dependency/* /app/

RUN mkdir /config
COPY --from=builder /src/main/resources/application_docker.conf /config/application.conf 
VOLUME /config

CMD exec java -Dconfig.path=/config -classpath /app/pipe-1.2.0.jar org.pipeoratopg.PipeBySizeDesc




