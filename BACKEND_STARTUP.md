# 🚀 Backend Startup Guide

本文档提供了该 Spring Boot + Spring AI 后端项目的环境配置与启动指南。

## 🛠 技术栈
- **Java**: JDK 17
- **框架**: Spring Boot 3.5.13
- **AI 框架**: Spring AI 1.1.4
- **数据库**: PostgreSQL (需安装 `pgvector` 插件)
- **缓存/记忆体**: Redis
- **构建工具**: Maven

---

## 📋 前置要求 (Prerequisites)

在启动项目之前，请确保本地已安装并启动以下服务：

### 1. JDK 17
确保系统已安装 Java 17 或以上版本。
```bash
java -version
```

### 2. PostgreSQL & pgvector (5432端口)
项目使用 PostgreSQL 存储核心数据，并使用 `pgvector` 插件作为向量数据库实现 RAG 检索。
- **推荐方案**：使用 Docker 启动带 `pgvector` 的 PostgreSQL 容器。
```bash
docker run -d --name pgvector \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=12345 \
  -e POSTGRES_DB=ai_db \
  -p 5432:5432 \
  ankane/pgvector
```
*（配置详见 `application.yaml` 中的 `spring.datasource` 节点）*

### 3. Redis (6379端口)
项目使用 Redis 来维护 Chat Memory（历史会话缓存）等。
- **启动 Redis**：
```bash
docker run -d --name redis -p 6379:6379 redis
```

---

## ⚙️ 环境变量配置

项目中使用了阿里云通义千问 (DashScope) 和 DeepSeek 大模型。为了保护密钥安全，建议通过环境变量或修改 `application.yaml` 配置大模型 API Keys：

在启动前需要配置以下环境变量（如果没有配置，会使用 `application.yaml` 中的默认测试 Key）：
- `DASHSCOPE_API_KEY`: 阿里云通义千问的 API Key
- `DEEPSEEK_API_KEY`: DeepSeek 的 API Key

*可选的覆盖变量（对应服务端口与数据库）*：
- `SERVER_PORT`: (默认 `10001`)
- `DB_HOST`: (默认 `localhost`)
- `DB_PORT`: (默认 `5432`)
- `DB_NAME`: (默认 `ai_db`)
- `DB_USERNAME`: (默认 `postgres`)
- `DB_PASSWORD`: (默认 `12345`)
- `REDIS_HOST`: (默认 `localhost`)
- `REDIS_PORT`: (默认 `6379`)

---

## 🏃 启动步骤

### 方法 1: 使用 IDE 启动 (推荐)
1. 使用 IntelliJ IDEA 导入该 Maven 项目。
2. 确保 Project JDK 设置为 17。
3. 如果未初始化数据库表结构，请参考 `src/main/resources/schema.sql` (若有) 检查是否需要手动建表，或者依赖 Spring 自动建表。
4. 找到并运行主启动类（例如 `Ai130Application.java`）。

### 方法 2: 使用 Maven 命令行启动
在项目根目录（`pom.xml` 所在目录）打开终端，运行以下命令：

```bash
# Windows
mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

---

## 🧪 验证启动
启动成功后，控制台会打印 Spring 启动标志。默认服务器运行在 **10001** 端口。

如果出现类似以下日志，则代表启动成功：
```text
Tomcat initialized with port 10001 (http)
Started Ai130Application in X.XXX seconds
```
你可以向本地后端发起请求进行测试，或者结合 `FRONTEND_REQUIREMENTS.md` 开发并联调前端。

---

## 📌 常见问题 (Troubleshooting)

**1. 数据库连接失败**
- 检查 PostgreSQL 容器是否正常启动：`docker ps`
- 检查密码是否为 `12345` 以及用户名是否是 `postgres`。

**2. 缺少 pgvector 报错**
- 若提示 `type "vector" does not exist`，说明当前的 PostgreSQL 不支持向量，必须安装/使用带 `pgvector` 的 PostgreSQL 镜像。

**3. API Key 无效/请求模型报错**
- 请前往对应的服务商控制台生成有效的 API Key，并在 IDE 配置环境变量后重新启动应用。
