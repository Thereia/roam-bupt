# 构建阶段：用 Maven 打可执行 jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY roam-api/pom.xml roam-api/
COPY roam-core/pom.xml roam-core/
COPY roam-service/pom.xml roam-service/
COPY roam-web/pom.xml roam-web/
# 先拉依赖，源码变化时可复用这一层
RUN mvn -q -B -DskipTests dependency:go-offline
COPY . .
RUN mvn -q -B -DskipTests -pl roam-web -am package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/roam-web/target/roam-web-*.jar app.jar
RUN mkdir -p /app/uploads
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
