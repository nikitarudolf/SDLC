# Сборка
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Запуск
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 labqueue
COPY --from=build /src/target/labqueue.jar app.jar
USER labqueue
EXPOSE 8080
ENV JAVA_OPTS="-Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
