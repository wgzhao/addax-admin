# Addax Admin 项目长期记忆

## 项目定位
Addax ETL 引擎的企业级管理平台，提供采集表配置、调度执行、运行监控、日志审计一站式运维。

## 技术栈
- 后端: Java 21 + Spring Boot 3.5.6 + PostgreSQL 16 + Redis
- 前端: Vue 3.5 + TypeScript + Vuetify 4.1 + Vite 8 + Pinia 3
- 构建: `bun build:frontend` / `bun build:backend`
- 本地测试: `VITE_API_HOST=http://localhost:50601 bun run dev` → http://localhost:3030

## 架构要点
- Master/Worker 分布式: Redis 选举 + 心跳 + Pub/Sub 分发
- 适配器模式: TargetAdapter (Hive / RDBMS) 注册中心路由
- 三层调度: 源级 Cron + 表级覆盖(2min补偿) + 通用 Cron
- DB 队列: PG LISTEN/NOTIFY + FOR UPDATE SKIP LOCKED + 租约 + 指数退避

## 核心文件
- `TaskQueueManagerV2Impl.java`: 分布式队列核心 (1097行)
- `TargetServiceWithHiveImpl.java`: Hive 表结构演进
- `JobContentService.java`: Addax JSON 任务编排
- `TableService.java`: 表结构演化接口
- `TaskSchedulerService.java`: 调度接口

## 代码规范
- 中文回复，代码注释英文写 why 不写 how
- Conventional Commits + Husky/commitlint 校验
- 前端文件路由: `<route>` 块声明 meta，导航自动生成
- 构造器注入 + Lombok，Java Records/Text Blocks
- 禁止随意修改 .env / application.properties
