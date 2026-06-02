# ============================================================
# ai130-backend Dockerfile — Spring Boot 多阶段构建
#
# 构建：
#   cd ../ai130
#   docker build -t ai130-backend .
#
# 运行（手动调试用，生产由 docker-compose 管理）：
#   docker run -d --name ai130-backend --network ai130-net \
#     -e SERVER_PORT=8082 \
#     -e DB_HOST=postgres \
#     -e REDIS_HOST=redis \
#     -e DASHSCOPE_API_KEY=sk-xxx \
#     -e DEEPSEEK_API_KEY=sk-xxx \
#     ai130-backend
# ============================================================

# Stage 1: 编译
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 利用 Docker 层缓存：先拷贝依赖文件
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# 拷贝源码并编译
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: 运行
FROM eclipse-temurin:17-jre
WORKDIR /app

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]