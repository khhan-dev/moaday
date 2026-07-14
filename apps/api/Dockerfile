FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S moaday && adduser -S moaday -G moaday && mkdir -p /data/moaday && chown -R moaday:moaday /data/moaday
WORKDIR /app
COPY --from=build /workspace/target/moaday-api-*.jar /app/app.jar

USER moaday:moaday
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
