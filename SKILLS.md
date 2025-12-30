# Addax Admin — SKILLS (与 AI 交互指南)

此文档为 AI 与开发者后续交互准备的“技能卡片”，概述项目结构、运行方式、关键文件、常见故障与建议的交互格式。推荐在请求 AI 做具体修改或分析时将本文件作为上下文引用。

---

## 一、项目概要

- 名称：Addax Admin（Monorepo）
- 组成：backend（Spring Boot 3 + Java 21） + frontend（Vue 3 + TypeScript + Vite + Vuetify）
- 语言：Java、TypeScript、HTML/CSS、Shell
- 包管理与构建：Maven（后端）、Yarn/Vite（前端）

主要目录：
- /backend — 后端服务（Maven 模块）
- /frontend — 管理 UI（Yarn workspace: addax-ui）
- /scripts — 部署/运维脚本、SQL 初始化脚本
- /logs — 运行日志

---

## 二、快速启动（开发）

推荐使用仓库自带的一键脚本：

1) 准备环境
- Java 21
- Maven 3.8+
- Node.js >= 16
- Yarn (或 npm)
- PostgreSQL、Redis（用于任务调度/仲裁）

2) 一键启动（项目根目录）：

./start-dev.sh

脚本行为：
- 安装前端依赖（如果 frontend/node_modules 缺失）
- 启动前端（yarn dev:frontend）并在后台运行
- 启动后端（mvn spring-boot:run）并在后台运行
- 在仓库根目录生成 ./stop-dev.sh 用于停止服务

注意：项目中对于前端端口存在不一致说明（README.md 中一处写 5173，frontend/vite.config.ts 配置的是 3030）。以 frontend/vite.config.ts 中的 server.port=3030 为准（开发服务器）。后端默认端口由 backend/src/main/resources/application.properties 指定为 50601。

手动启动示例：
- 初始化数据库并导入：
  psql -U postgres -d your_database -f backend/src/main/resources/schema.sql
  psql -U postgres -d your_database -f backend/src/main/resources/data.sql
- 启动后端（开发）：
  cd backend && mvn spring-boot:run
- 或启动后端 jar：
  java -jar backend/target/addax-admin-1.0.0-SNAPSHOT.jar （需提前打包）
- 启动前端：
  cd frontend && yarn install && yarn dev

访问地址（开发）：
- 前端（Vite dev server）：http://localhost:3030
- 后端 API：http://localhost:50601/api/v1
- Swagger UI（默认位置）：http://localhost:50601/api/v1/swagger-ui/index.html

---

## 三、关键文件与位置（快速参考）

- 根
  - README.md、start-dev.sh
- 后端（backend）
  - pom.xml（模块构建）
  - src/main/java/com/wgzhao/addax/admin/AdminApplication.java（Spring Boot 启动类）
  - src/main/resources/application.properties（默认配置：端口、DB/Redis、JWT 过期等）
  - src/main/resources/schema.sql、data.sql（初始化数据库脚本）
  - src/main/java/.../controller、service、repository、scheduler（业务实现）
  - src/main/java/com/wgzhao/addax/admin/common/Constants.java（常量配置，锁 key 等）
  - backend/config/env.template.sh（env 示例，用于生产/运维）
- 前端（frontend）
  - package.json（脚本与依赖）
  - vite.config.ts（Vite 配置、代理与端口）
  - src/（components、views、service、stores、utils）
  - src/service/*-service.ts（与后端交互的 API 层）

---

## 四、重要依赖及技术栈要点

后端（pom）亮点：
- Spring Boot 3.5.6, Spring Data JPA, Spring Security
- Hibernate 6.6.11
- PostgreSQL 驱动
- Redis（spring-boot-starter-data-redis）用于锁与仲裁
- jjwt 用于 JWT

前端（package.json）亮点：
- Vue 3 + TypeScript + Vite
- Vuetify 3，Pinia，Vue Router
- Chart.js、Monaco Editor（代码编辑）

---

## 五、配置与环境变量（可通过 env 文件覆盖）

后端（application.properties）使用占位符，可通过环境变量注入：
- DB_HOST（默认 localhost）
- DB_PORT（默认 5432）
- DB_NAME（默认 addax_admin）
- DB_USERNAME / DB_PASSWORD（默认 addax_admin / addax_admin@123）
- REDIS_HOST / REDIS_PORT / REDIS_PASSWORD / REDIS_DB
- LOG_DIR（默认 ./logs）
- node.concurrency.weight（WEIGHT）
- WECOM_ROBOT_KEY（企业微信推送机器人）

运维模板：backend/config/env.template.sh

前端（env）
- VITE_API_BASE_URL（例如 /api 或 /api/v1）
- VITE_API_HOST（后端主机，例如 http://localhost:50601）

注意：启动脚本/部署时请确保这些环境变量已正确设置并能被 JVM / Node 进程读取。

---

## 六、常见操作（命令）

仓库根（yarn workspace）相关：
- yarn install
- yarn dev（等价于 yarn dev:frontend）
- yarn build
- yarn build:frontend
- cd backend && mvn clean package / mvn spring-boot:run

前端：
- cd frontend && yarn install
- yarn dev（启动开发服务器）
- yarn build（生产构建）
- yarn preview（预览构建）

后端：
- cd backend && mvn spring-boot:run
- cd backend && mvn clean package（打包 jar 或 assembly）
- java -jar backend/target/addax-admin-1.0.0-SNAPSHOT.jar

调试日志文件：/logs 和 backend/logs。后端 logback 配置在 src/main/resources/logback-spring.xml。

---

## 七、AI 可帮助的常见任务（以及如何提出请求）

建议在请求中包含：
- 期望的改动范围（具体文件路径或类名）
- 复现步骤或要达到的结果
- 任何相关日志或错误栈（粘贴关键行）
- 目标环境（dev/production，Java/Node 版本）

示例请求模板：
- "请帮我在 backend/src/main/java/com/wgzhao/addax/admin/service/TaskService.java 中把 X 方法改为 Y，修改后请提供需要的单元测试与运行验证步骤。"
- "前端在 table.vue 页面渲染慢，请定位可能的性能问题（提供组件路径），并给出代码修复建议与性能验证方法。"

AI 可以直接帮助的任务：
- 代码阅读与概要（定位类与方法责任）
- 使用路径级别修改（生成补丁或给出 diff）
- 编写/更新 SQL migration 与初始化脚本
- 提取并分析日志、定位异常堆栈、建议修复
- 升级依赖并评估破坏性变更
- 设计 API、数据库变更并给出回滚/迁移方案
- 生成测试用例（单元、集成）与 CI/CD 建议

---

## 八、常见故障排查要点

- 端口冲突：后端默认 50601，前端默认 3030（检查是否被占用）
- 数据库连接失败：确认 DB_HOST/DB_PORT/DB_NAME/用户名/密码 与 schema.sql 导入匹配
- Redis 连接问题：确认 spring.data.redis.* 环境变量
- JWT/认证问题：检查 jwt 配置与 token 生成逻辑（JwtService）
- 前端代理问题：vite.config.ts 中 proxy 使用 VITE_API_BASE_URL->VITE_API_HOST，请确保 env 文件一致
- 权限/文件权限：日志目录（./logs）需要可写权限

---

## 九、代码修改与 PR 流程建议（AI 可直接执行的步骤）

1) 明确目标与破坏面（列出受影响文件）
2) 由 AI 生成修改补丁（最好给出文件 diff）
3) 本地运行 yarn dev / mvn spring-boot:run，并执行相关验证脚本或手工验证页面/API
4) 生成单元测试或集成测试（若需要）
5) 提交 PR，附上说明与回归测试要点

---

## 十、其他注意事项与已知不一致点

- README.md 中关于前端默认端口有不一致（5173 vs 3030）。优先信任 frontend/vite.config.ts 中配置（3030）。
- start-dev.sh 启动后端的命令会在后台执行 mvn spring-boot:run，打包或生产部署请使用 mvn clean package 并执行 jar。
- 若需要容器化（Dockerfile 未包含在此仓库），建议为后端/前端分别构建镜像并使用 docker-compose 来组合 Postgres/Redis/服务。

---

如果你想让我现在生成一个 PR、修改某个文件或补全文档，请直接告诉我：
- 要修改的目标（文件路径或功能描述）
- 期望的行为/输出
- 是否需要同时运行测试/编译

我可以：
- 生成修改补丁并写入仓库（或给出完整 diff）
- 为修改创建 todo 列表并按步执行
- 运行构建/测试命令并报告输出（需授权执行）

祝开发顺利！
