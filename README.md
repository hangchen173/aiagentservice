# NexusMind 多智能体客服中枢

NexusMind 是一个多智能体客服系统，技术栈为 Java 21、Spring Boot、Spring AI、MyBatis-Plus、PostgreSQL、Vue 3 和 WebSocket。文本服务使用 DeepSeek，图片识别使用 Qwen VL。

## 功能

- JWT 三角色登录：管理员、客服坐席、注册用户。
- 用户公开注册，管理员和客服账号仅通过本机环境变量预置。
- 访客聊天接入，支持 WebSocket 实时消息。
- 规则调度多个客服智能体：通用、售前、售后、投诉、转人工。
- 默认文本模型配置为 `deepseek-v4-flash`。
- 访客可上传 JPG、PNG、GIF 图片，并使用独立的 Qwen VL 模型识别图片内容。
- AI 回复、模型调用日志、会话消息入库。
- 明确触发人工、投诉、退款时创建转人工记录和工单。
- 客服可接单、实时回复、关闭和删除已完成工单；管理员可清理全部业务历史。

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
- `V2__seed_reference_data.sql`：历史参考数据迁移，其演示账号会由 V4 停用。
- `V4__secure_accounts_and_deepseek.sql`：停用旧演示账号，并迁移文本模型配置。

新增或修改表结构时，不要直接改已经发布过的迁移文件；应新增下一版脚本，例如：

```text
V3__add_ticket_sla_fields.sql
```

应用启动时会自动执行未应用的迁移，并在数据库中记录到 `flyway_schema_history`。Docker Compose 不再把 SQL 挂载进 PostgreSQL 初始化目录，避免只有首次创建 volume 才生效的问题。

## AI 配置

项目根目录已经提供 `.env` 和 `.env.example`。本地开发时把真实 Key 写入 `.env`：

```properties
DEEPSEEK_API_KEY=你的DeepSeek Key
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-v4-flash
DASHSCOPE_API_KEY=你的百炼或DashScope Key
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode
QWEN_VISION_MODEL=qwen-vl-max
JWT_SECRET=请替换为更长的随机密钥
BOOTSTRAP_ADMIN_USERNAME=管理员账号
BOOTSTRAP_ADMIN_PASSWORD=至少12位强密码
BOOTSTRAP_AGENT_USERNAME=客服账号
BOOTSTRAP_AGENT_PASSWORD=至少12位强密码
```

文本与视觉调用使用相互独立的 Spring AI 客户端。`DEEPSEEK_API_KEY` 用于文本及流式回复，`DASHSCOPE_API_KEY` 仅用于 Qwen 图片识别；未配置真实 Key 时使用本地演示回复。管理员和客服账号在每次启动时由环境变量同步到 PostgreSQL，密码只以哈希形式保存。

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
