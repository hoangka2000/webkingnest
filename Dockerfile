FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN mkdir -p /app/data

COPY --from=build /app/target/yen-sao-web-*.jar /app/app.jar
COPY data/kingnest.mv.db /app/data/kingnest.mv.db

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
