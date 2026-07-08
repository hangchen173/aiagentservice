# NexusMind 多智能体客服中枢

NexusMind 是一个面向企业实训演示的多智能体客服系统，技术栈为 Java 21、Spring Boot、Spring MVC、MyBatis-Plus、PostgreSQL、Vue 3、WebSocket 和 Qwen 模型适配。

## 功能

- JWT 三角色登录：管理员、客服坐席、访客。
- 访客聊天接入，支持 WebSocket 实时消息。
- 规则调度多个客服智能体：通用、售前、售后、投诉、转人工。
- 默认模型配置为 `qwen3.7-plus`。
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

访问地址：

- 前端容器：`http://localhost:3000`
- Vue 开发服务：`http://localhost:5173`
- 后端：`http://localhost:8080`
- PostgreSQL 容器映射端口：`55432`

## AI 配置

项目根目录已经提供 `.env` 和 `.env.example`。本地开发时把真实 Key 写入 `.env`：

```properties
DASHSCOPE_API_KEY=你的百炼或DashScope Key
QWEN_MODEL=qwen3.7-plus
JWT_SECRET=请替换为更长的随机密钥
```

当前 `DashScopeClient` 使用阿里云百炼/DashScope OpenAI 兼容接口调用模型，并保留 DashScope Java SDK 依赖。配置 `DASHSCOPE_API_KEY` 后会调用数据库中启用的模型，默认是 `qwen3.7-plus`；未配置 API Key 时会返回本地演示回复，保证系统闭环可运行。
