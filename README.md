# NexusMind 多智能体客服中枢

NexusMind 是一个面向企业实训演示的多智能体客服系统，技术栈为 Java 21、Spring Boot、Spring AI、Spring MVC、MyBatis-Plus、PostgreSQL、Vue 3、WebSocket 和 Qwen 模型适配。

## 功能

- JWT 三角色登录：管理员、客服坐席、访客。
- 访客聊天接入，支持 WebSocket 实时消息。
- 规则调度多个客服智能体：通用、售前、售后、投诉、转人工。
- 默认模型配置为 `qwen3.7-plus`。
- 访客可上传 JPG、PNG、GIF 图片，并使用独立的 Qwen VL 模型识别图片内容。
- AI 回复、模型调用日志、会话消息入库。
- 明确触发人工、投诉、退款时创建转人工记录和工单。
- 管理端支持会话、工单、智能体、模型、调用日志查看与部分编辑。

## 默认账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |
| 客服 | `agent` | `agent123` |
| 访客 | `visitor` | `visitor123` |

## 本地启动

后端需要 Java 21 和 Maven：

```bash
mvn test
mvn spring-boot:run
```

前端需要 Node.js 20+ 或 22+：

```bash
cd frontend
npm install
npm run dev
```

Docker 一键启动：

```bash
docker compose up --build
```

多实例 WebSocket/Redis 模式验收：

```bash
docker compose up --build --scale backend=2
```

Compose 中后端会打开 `WEBSOCKET_REDIS_PUBSUB_ENABLED=true` 和 `RATE_LIMIT_REDIS_ENABLED=true`。两个后端实例会共享 Redis Pub/Sub 广播和 Redis 限流计数；前端容器仍通过 `http://localhost:3000` 访问。验收时可以打开两个浏览器窗口加入同一会话，发送消息后应能看到增量 AI 回复和工单通知正常推送。

访问地址：

- 前端容器：`http://localhost:3000`
- Vue 开发服务：`http://localhost:5173`
- 后端：`http://localhost:8080`
- PostgreSQL 容器映射端口：`55432`

## 数据库迁移

项目使用 PostgreSQL 作为主数据库，并通过 Flyway 管理可迁移的数据库结构。迁移脚本位于：

```text
src/main/resources/db/migration
```

当前迁移：

- `V1__init_schema.sql`：创建表、外键、约束和常用索引。
- `V2__seed_reference_data.sql`：写入默认账号、模型和智能体种子数据。

新增或修改表结构时，不要直接改已经发布过的迁移文件；应新增下一版脚本，例如：

```text
V3__add_ticket_sla_fields.sql
```

应用启动时会自动执行未应用的迁移，并在数据库中记录到 `flyway_schema_history`。Docker Compose 不再把 SQL 挂载进 PostgreSQL 初始化目录，避免只有首次创建 volume 才生效的问题。

## AI 配置

项目根目录已经提供 `.env` 和 `.env.example`。本地开发时把真实 Key 写入 `.env`：

```properties
DASHSCOPE_API_KEY=你的百炼或DashScope Key
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode
QWEN_MODEL=qwen3.7-plus
QWEN_VISION_MODEL=qwen-vl-max
JWT_SECRET=请替换为更长的随机密钥
```

当前模型调用层使用 Spring AI `ChatClient`，通过阿里云百炼/DashScope OpenAI 兼容接口调用 Qwen。配置 `DASHSCOPE_API_KEY` 后会调用数据库中启用的模型，默认是 `qwen3.7-plus`；未配置 API Key 时会走本地演示网关，保证系统闭环可运行。

AI 调用相关保护参数：

```properties
AI_TIMEOUT_SECONDS=60
AI_HTTP_CONNECT_TIMEOUT_SECONDS=5
AI_HTTP_READ_TIMEOUT_SECONDS=90
AI_MAX_TOKENS_LIMIT=600
AI_EXECUTOR_CORE_SIZE=4
AI_EXECUTOR_MAX_SIZE=8
AI_EXECUTOR_QUEUE_CAPACITY=40
CHAT_IMAGE_STORAGE_PATH=./data/chat-images
CHAT_IMAGE_MAX_BYTES=8388608
```

其中业务层超时负责用户兜底回复和调用日志，HTTP 读取超时应大于业务层超时，避免底层连接先把慢响应误判为模型故障。访客使用 WebSocket 发送文本消息时，后端会通过 Spring AI streaming 推送增量回复；图片通过受认证的 multipart 接口上传，并由 `QWEN_VISION_MODEL` 分析。图片默认最大 8 MB，保存在受保护的本地目录中；Docker Compose 使用共享命名卷保存附件。未配置真实 API Key 时，本地演示网关会明确提示无法执行真实图片识别。
