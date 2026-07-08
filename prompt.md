你是一名资深全栈工程师和 DevOps 顾问。请基于我当前的 NexusMind 多智能体客服中枢项目，设计一套“不购买云服务器，但可以临时公网访问和演示”的部署方案。

项目背景：
- 项目名称：NexusMind 多智能体客服中枢系统
- 技术栈：
  - Java 21
  - Spring Boot
  - Spring MVC
  - MyBatis-Plus
  - PostgreSQL
  - Redis
  - Vue 3
  - Vite
  - TypeScript
  - Element Plus
  - WebSocket
  - DashScope / qwen3.7-plus
- 当前项目已经可以本地运行：
  - PostgreSQL 和 Redis 通过 Docker Compose 启动
  - Spring Boot 后端运行在 localhost:8080
  - Vue 前端开发服务运行在 localhost:5173
  - 前端容器版本可运行在 localhost:3000
- 项目已有：
  - Dockerfile
  - frontend/Dockerfile
  - docker-compose.yml
  - .env
  - .env.example
  - PostgreSQL 初始化 SQL
  - Nginx 前端代理配置
- 我不想购买云服务器，也不想为学校实训项目持续付费。
- 我希望只有当我的电脑开机并启动项目时，网站才可以被公网访问；电脑关闭或项目停止后，网站下线。
- 我希望这个方案仍然能体现“项目支持容器化部署，未来迁移到任意服务器成本很低”。

请输出一份完整方案，要求包括：

1. 总体结论
   - 明确说明“不买服务器也可以临时公网访问”
   - 说明域名不等于服务器
   - 说明本机 + 公网隧道可以作为临时演示部署方式

2. 推荐部署路线
   - 首推：本地 Docker 环境 + Cloudflare Tunnel
   - 备选：本地 Docker 环境 + ngrok
   - 说明两者区别：
     - ngrok 更适合快速临时演示
     - Cloudflare Tunnel 更适合绑定自己的域名和长期保留配置

3. 目标架构
   用文字和 ASCII 图说明：
   - 用户浏览器
   - 公网域名 / 临时隧道地址
   - Cloudflare Tunnel 或 ngrok
   - 本机前端服务
   - 本机 Spring Boot 后端
   - 本机 Docker PostgreSQL / Redis
   - DashScope qwen3.7-plus

4. 本地服务启动步骤
   给出详细命令：
   - 进入项目根目录
   - 检查 .env
   - 启动 PostgreSQL 和 Redis：
     docker compose up -d postgres redis
   - 启动后端：
     mvn spring-boot:run
   - 启动前端开发服务：
     cd frontend
     npm run dev
   - 访问本地：
     http://localhost:5173

5. ngrok 方案
   说明如何暴露前端：
   - ngrok http 5173
   - 或如果使用前端容器：
     ngrok http 3000
   说明得到的公网地址可以直接发给老师/同学访问。
   说明 ngrok 免费版的限制：
   - 地址可能变化
   - 可能有提示页
   - 国内网络可能不稳定
   - 适合临时答辩演示

6. Cloudflare Tunnel 方案
   说明两种模式：
   - 没有域名时：使用临时 trycloudflare 地址
     cloudflared tunnel --url http://localhost:5173
   - 有域名时：把域名接入 Cloudflare，再创建固定 Tunnel 和子域名
   说明 Cloudflare Tunnel 的优点：
   - 不需要服务器
   - 可绑定自有域名
   - 自动 HTTPS
   - 电脑关机后网站自动不可访问
   - 项目仍然运行在本机

7. 前后端代理说明
   说明为什么建议优先暴露前端地址：
   - 暴露 localhost:5173 时，Vite dev server 可以代理 /api 和 /ws 到 localhost:8080
   - 暴露 localhost:3000 时，前端容器 Nginx 需要正确代理 /api 和 /ws
   提醒检查：
   - /api 是否能访问后端
   - /ws 是否支持 WebSocket
   - CORS 是否已配置

8. 和真正云服务器部署的差异
   对比：
   - 本地 + Tunnel：
     - 不花服务器钱
     - 电脑开着才在线
     - 适合答辩/临时演示
   - 云服务器 + Docker Compose：
     - 长久在线
     - 更正式
     - 需要服务器费用
     - 需要 HTTPS、域名、备份、监控
   说明当前项目因为已有 Docker Compose，未来迁移到服务器大体只需要：
   - 安装 Docker
   - 上传代码
   - 配置 .env
   - 执行 docker compose up -d --build

9. 答辩时可以怎么讲
   帮我写一段专业但诚实的话，表达：
   - 本项目采用容器化设计
   - 当前演示环境采用本地 Docker + 公网隧道
   - 该方式不依赖云服务器，降低实训展示成本
   - 项目可平滑迁移到任意 Linux 云服务器
   - 迁移时只需要调整环境变量和域名配置

10. 安全注意事项
   - 不要把 DASHSCOPE_API_KEY 写进代码或提交到仓库
   - .env 必须加入 .gitignore
   - 公网演示期间不要开放数据库端口
   - 只暴露前端入口，不直接暴露 PostgreSQL、Redis
   - 演示结束后关闭 tunnel
   - 如果 API Key 曾经泄露，要去控制台轮换

11. 最终推荐
   根据我的情况给出明确建议：
   - 短期答辩：ngrok 或 Cloudflare 临时 tunnel
   - 想绑定域名：Cloudflare Tunnel
   - 不建议现在购买云服务器
   - 保留 Docker Compose 作为“可部署证明”

请用中文输出，语气务实，不要过度营销。内容要能直接放进项目部署文档或实习答辩准备材料里。