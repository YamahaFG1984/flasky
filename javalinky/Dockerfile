# ========== 阶段一：构建 ==========
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# 先只拷 pom.xml 并下载依赖 —— 这一层会被缓存，
# 只要 pom 没变，改代码就不用重新下载几百个 jar
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 再拷源码
COPY src ./src
RUN mvn -B clean package -DskipTests

# ========== 阶段二：运行 ==========
FROM eclipse-temurin:21-jre-alpine

# 不要用 root 跑应用
RUN addgroup -S javalinky && adduser -S -G javalinky javalinky

WORKDIR /app
COPY --from=build /build/target/javalinky.jar app.jar

# 日志目录要让应用用户能写
RUN mkdir -p /app/logs && chown -R javalinky:javalinky /app
USER javalinky

ENV APP_PROFILE=prod \
    PORT=8080 \
    LOG_DIR=/app/logs

EXPOSE 8080

# 健康检查：让编排系统知道容器是不是真的能服务
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD wget -q --spider http://localhost:8080/healthz || exit 1

# exec 形式（不是 shell 形式）：这样 java 进程的 PID 是 1，
# 能直接收到 docker stop 发的 SIGTERM，从而优雅关闭
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", \
            "-Dlogback.configurationFile=logback-prod.xml", \
            "-jar", "app.jar"]
