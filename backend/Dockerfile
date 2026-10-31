FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/sulu-backend-*.jar app.jar
RUN useradd -r sulu && mkdir -p /data/uploads && chown sulu /data/uploads
USER sulu
ENV SULU_STORAGE_DIR=/data/uploads
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
