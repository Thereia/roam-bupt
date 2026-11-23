# 构建阶段：用 Maven 打可执行 jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
# .m2 用 BuildKit 缓存挂载：源码改了也不用重新下载依赖
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests -pl roam-web -am package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/roam-web/target/roam-web-*.jar app.jar
# 健康检查需要 curl；temurin 基础镜像默认没有
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/uploads
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
