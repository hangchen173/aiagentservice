# NexusMind 问题优化清单

整理日期：2026-07-10

整理依据：

- 当前代码复核：鉴权、安全配置、会话服务、WebSocket、AI 调用、工单、前端控制台。
- 自动化验证：`mvn test`、`frontend npm run build`。
- 部署配置验证：`docker compose config`。

说明：

- 本文件已按当前工作树重新整理。
- 原清单中的 P0/P1/P2/P3 问题均已通过代码、配置、文档或可执行验收路径处理。
- 当前不保留未解决问题；后续若真实部署或验收发现新问题，再按优先级追加。

## 已解决

- 匿名 WebSocket 可加入任意会话：WebSocket 握手要求 JWT，业务动作使用握手中的 `AuthUser` 校验。
- WebSocket 与 REST 鉴权模型不一致：WebSocket 入口已接入身份上下文，`JOIN_SESSION` 等动作会走会话归属校验。
- VISITOR 可看到所有会话：`ChatService.listSessions()` 已按当前访客归属过滤。
- VISITOR 可读取他人消息：`ChatService.listMessages()` 已调用访问校验，不存在或无权访问会话不再返回消息。
- 演示访客账号复用：访客端改为调用 `/api/auth/visitor` 创建独立临时访客身份。
- AI 回复期间可重复发送：访客端发送中禁用输入和按钮，并保留长耗时提示。
- AI 长耗时没有后端兜底：模型调用增加业务超时、降级回复和 `maxTokens` 上限。
- AI 慢调用占用默认公共线程池：模型调用已迁移到专用 `aiModelExecutor`，线程数和队列容量可通过配置控制。
- AI 底层 HTTP 客户端缺少超时：新增 `AiHttpClientConfig`，将连接超时和读取超时传入 Spring AI OpenAI 底层 `RestClient`。
- AI 演示回复误判：演示网关只按访客消息分类，不再被系统提示词干扰。
- 真实流式输出缺失：WebSocket 聊天路径已接入 Spring AI streaming，向前端推送 `AI_MESSAGE_DELTA` 和 `AI_MESSAGE_DONE`；无真实 Key 时演示网关会模拟增量输出。
- 创建会话无 body 返回 500：全局异常处理统一返回 `{ success, data, message }` 风格错误。
- 参数校验、权限错误响应不统一：全局异常处理和 Spring Security 401/403 均返回统一 JSON。
- 缺少基础限流：`RateLimitFilter` 已接入安全过滤链，覆盖登录、聊天、工单、路由预览和 WebSocket 入口。
- 限流仅支持单机内存：新增 `RateLimitStore` 抽象，默认使用内存实现；设置 `RATE_LIMIT_REDIS_ENABLED=true` 后使用 Redis 计数，支持多实例共享限流。
- 转人工重复创建工单：同一会话已有 `OPEN` 或 `PROCESSING` 工单时复用，不再重复创建。
- 自动创建工单缺少系统消息：自动转人工路径会落库 `SYSTEM` 消息。
- `handoff` 优先级压过 `complaint`：`handoff` 从普通业务回复路由中剥离，投诉消息仍优先进入投诉智能体，同时保留转人工建议。
- 测试/演示数据持续累积：新增管理员接口 `DELETE /api/admin/demo-data`，并在控制台提供“清理演示数据”按钮。
- 查询不存在会话消息语义不清：当前服务会先校验会话存在性和归属，不存在时返回错误而不是 200 空数组。
- 访客页打开即创建空会话：访客页延迟到首次发送消息或点击转人工时创建会话。
- 移动端控制台基础布局：顶部栏、侧边菜单、指标区、面板和输入区已补充窄屏响应式样式。
- 移动端控制台只能轻量操作：当前已保证移动端可查看和完成基础操作；若未来需要移动客服高频工作台，应作为新产品需求单独设计。
- 前端主包过大：路由改为懒加载，Element Plus 改为按需注册，构建产物不再出现主 chunk 超 500 kB 告警。
- 前端生产构建存在上游依赖注释告警：Vite custom logger 已只过滤 `@vueuse/core` 的已知无害 `INVALID_ANNOTATION`，保留其他构建警告和错误。
- MyBatis SQL 日志过于嘈杂：默认配置已移除 `StdOutImpl`。
- Flyway 接管已有库日志噪声：baseline 策略已调整，减少历史库接管时的可预期警告。
- Redis/内存 WebSocket 架构不支持可靠水平扩展：Compose 默认为后端开启 Redis Pub/Sub，移除 backend 固定容器名并配置端口范围，支持 `docker compose up --build --scale backend=2` 做多实例验收。
- 缺少 favicon：当前 `frontend/public/favicon.ico` 已存在。
- `controller` 文件夹为空：当前 Controller 按业务包组织，不存在空 controller 目录问题。

## 验证记录

- `mvn test`：通过。
- `cd frontend && npm run build`：通过，构建输出无阻断告警。
- `docker compose config`：通过，确认 Redis、后端扩容端口范围和相关环境变量配置有效。

## 后续观察项

- 多实例 WebSocket 需要在真实容器运行时按 README 的 `docker compose up --build --scale backend=2` 做人工验收；当前已提供代码支持和可执行部署配置。
- 移动端控制台已满足基础可用，若要做正式移动客服工作台，应作为新功能需求重新设计信息架构。
