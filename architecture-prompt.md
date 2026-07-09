你是一名资深企业级 Java 全栈架构师、Spring Boot 后端负责人和 Vue 前端工程负责人。请基于我当前的 NexusMind 多智能体客服中枢项目，设计并指导我将项目演进为“业务模块内部分层”的企业级模块化单体架构。

项目背景：
- 项目名称：NexusMind 多智能体客服中枢系统
- 技术栈：
  - Java 21
  - Spring Boot
  - Spring MVC
  - Spring Security
  - MyBatis-Plus
  - PostgreSQL
  - Redis
  - WebSocket
  - Vue 3
  - Vite
  - TypeScript
  - Element Plus
  - DashScope / qwen3.7-plus
- 项目核心业务：
  - 用户登录与 JWT 鉴权
  - 访客创建会话
  - WebSocket 实时聊天
  - AI 智能体路由
  - 大模型调用与调用日志
  - 访客转人工
  - 工单创建与处理
  - 管理员/客服控制台
- 当前项目已经按业务模块初步分包，例如：
  - auth
  - chat
  - ticket
  - agent
  - aimodel
  - ws
  - security
  - common
  - config
- 当前项目还存在一些不够企业级的地方：
  - 部分 Controller、Service、Request DTO 直接放在业务包根目录
  - mapper 和 entity 仍然是全局目录
  - 部分接口直接返回数据库实体
  - 缺少清晰的 api / application / domain / infrastructure 分层
  - 前端模块边界还可以继续拆分

请输出一份完整的企业级架构演进方案，目标是：

业务模块内部分层的企业级模块化单体。

请重点说明：

1. 总体架构结论
   - 明确说明本项目不建议一开始拆微服务
   - 推荐采用 Modular Monolith，也就是模块化单体
   - 推荐按业务模块组织代码，而不是使用全局 controller/service/dto 大目录
   - 说明“模块化单体”和“传统三层目录”的区别
   - 说明这种架构为什么适合多智能体客服系统

2. 推荐后端目录结构
   请给出完整目录树，至少包含：
   - common
   - config
   - security
   - auth
   - chat
   - ws
   - agent
   - aimodel
   - ticket

   每个业务模块内部按以下结构组织：
   - api
   - api/request
   - api/response
   - application
   - domain
   - infrastructure

   示例结构请类似：

   src/main/java/com/intern
     chat
       api
         ChatController.java
         request
           CreateSessionRequest.java
           SendMessageRequest.java
         response
           ChatSessionResponse.java
           ChatMessageResponse.java
       application
         ChatService.java
         ChatQueryService.java
         ChatCommandService.java
       domain
         ChatSession.java
         ChatMessage.java
         ChatSessionStatus.java
         SenderType.java
         ChatOwnershipPolicy.java
       infrastructure
         ChatSessionMapper.java
         ChatMessageMapper.java
         ChatRepository.java

3. 每一层职责说明
   请解释每层应该做什么、不应该做什么：
   - api：HTTP/WebSocket 入参、参数校验、返回 Response DTO
   - application：业务流程编排、事务边界、跨领域协作
   - domain：核心业务对象、枚举、规则、策略
   - infrastructure：数据库、Redis、外部模型 SDK、消息发布、第三方接口适配
   - common：统一响应、异常、分页、通用工具
   - security：认证、授权、JWT、当前用户、限流
   - config：Spring 配置、WebSocket 配置、MyBatis 配置、Redis 配置

4. 当前项目到目标架构的映射
   请把当前类似这些文件映射到新结构：
   - ChatController
   - ChatService
   - CreateSessionRequest
   - SendMessageRequest
   - ChatSession
   - ChatMessage
   - ChatSessionMapper
   - TicketController
   - TicketService
   - AgentController
   - AgentService
   - AiModelController
   - AiModelService
   - WsPublisher
   - ChatWebSocketHandler
   - WsAuthHandshakeInterceptor
   - JwtService
   - SecurityConfig
   - GlobalExceptionHandler

5. DTO 设计规范
   请说明为什么企业级项目不建议直接返回 entity。
   请设计以下 DTO：
   - ChatSessionResponse
   - ChatMessageResponse
   - TicketResponse
   - AiAgentResponse
   - AiModelResponse
   - ModelCallLogResponse
   - UpdateTicketStatusRequest

   要求说明：
   - Request DTO 和 Response DTO 分开
   - DTO 不包含 deleted 等内部字段
   - DTO 作为前后端契约
   - entity 可以变化，但接口契约尽量稳定

6. 领域对象和持久化对象如何处理
   请基于当前项目规模给出务实建议：
   - 当前阶段可以先让 entity 继续作为持久化对象
   - 后续复杂后再区分 domain model 和 persistence entity
   - 不要为了架构而过度抽象
   - 但接口层应尽快避免直接暴露 entity

7. WebSocket 与 REST 的统一安全模型
   请说明：
   - REST 和 WebSocket 都必须复用同一套身份认证和会话归属校验
   - WebSocket 握手阶段校验 JWT
   - 加入会话、发送消息、转人工都必须经过 application/domain 层权限判断
   - 不允许只在前端隐藏按钮来做权限控制

8. AI 与工单业务的模块协作
   请说明一条完整链路应该如何跨模块协作：
   - 访客发送消息
   - chat application 保存访客消息
   - agent application 判断命中智能体
   - aimodel application 调用模型
   - chat application 保存 AI 回复
   - ticket application 在需要时创建工单
   - ws application 推送实时消息

   请说明 application 层如何编排，domain 层如何沉淀规则。

9. 前端推荐目录结构
   请给出 Vue 3 企业级前端目录结构，例如：

   frontend/src
     app
       main.ts
       App.vue
       router.ts
     shared
       api
       auth
       components
       styles
       types
     modules
       visitor-chat
         views
         api
         types.ts
       console
         views
         api
         types.ts
       auth
         views
         api
         store

   请解释为什么前端也应该按业务模块拆，而不是把所有 views/api/types 混在一起。

10. 推荐演进顺序
   请给出低风险、可逐步提交的重构路线，不要建议一次性大重构。
   推荐顺序请包含：
   - 第一步：补 Response DTO，避免接口直接返回 entity
   - 第二步：移动 Request DTO 到 api/request
   - 第三步：移动 Controller 到 api
   - 第四步：移动 Service 到 application
   - 第五步：增加 domain 枚举和策略类
   - 第六步：逐步移动 mapper 到 infrastructure
   - 第七步：前端按 modules 拆分
   - 第八步：补测试，保证每次移动都不改变行为

11. 测试策略
   请说明企业级项目应该覆盖：
   - Controller 参数校验测试
   - application service 业务流程测试
   - 权限/归属校验测试
   - WebSocket 鉴权测试
   - AI 路由策略测试
   - 工单状态流转测试
   - 前端构建和关键交互测试

12. 命名规范
   请给出命名建议：
   - Controller 只放 api 层
   - Service 放 application 层
   - Policy / Rule / Specification 放 domain 层
   - Mapper / Gateway / Repository / Publisher 放 infrastructure 层
   - Request / Response DTO 放 api/request 和 api/response

13. 不建议做的事
   请明确说明：
   - 不建议全局 controller/service/dto 大目录
   - 不建议现在拆成微服务
   - 不建议接口直接返回数据库实体
   - 不建议为了架构新增大量空目录和无意义抽象
   - 不建议把业务权限只写在 Controller 或前端

14. 最终交付
   请用中文输出。
   语气要务实、偏企业开发实践，不要过度营销。
   内容要能直接作为项目架构设计文档或重构任务说明使用。
   请给出：
   - 推荐目录结构
   - 当前文件迁移表
   - 分阶段重构计划
   - 每阶段验收标准
   - 风险点和注意事项
