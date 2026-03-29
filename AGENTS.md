# AGENTS.md

该文件旨在为 AI 提供关于该项目的开发和维护的指导原则和最佳实践。请遵循以下准则，以确保代码质量、可维护性和团队协作的顺利进行。

## 项目概述

该项目为 [Addax](https://github.com/wgzhao/addax) 提供了管理界面和调度服务，在该管理工具的支持下，可以快速批量完成采集表的创建、修改和调度。核心功能包括：

- 采集表管理：支持表结构演化、动态表名和版本控制
- 调度服务：基于数据库持久化队列和 Redis 仲裁的混合并发控制架构
- 任务编排：支持采集任务文件的编排和更新，确保任务依赖关系正确处理

## 禁止

- 随意修改 `.env` 文件, `application.properties` 文件，使用环境变量方式临时覆盖配置
- 随意移除功能标志而不搜索所有调用点
- 提交代码前不运行测试
  
## 编译

- 前端: `yarn build:frontend`
- 后端: `yarn build:backend`

## 本地测试流程

1. `yarn build:backend` 编译后端服务
2. 执行 `/opt/app/addax-admin/service.sh restart` 重启本地的后端服务
3. 执行 `VITE_API_HOST=http://localhost:50601 bun run dev` 启动前端开发服务器
4. 访问 `http://localhost:3030` 进行功能测试

## 系统目录结构

```ini
addax-admin/
├── backend/                 # Spring Boot 3 后端服务（API、调度、持久化、Redis 仲裁）
├── frontend/                # Vue 3 + Vite + TypeScript + Vuetify 管理界面
├── scripts/                 # 部署与运维脚本（DB 初始化、systemd service 模板等）
└── README.md                # 本文档
```

## 核心文件说明

- [TargetServiceWithHiveImpl](backend/src/main/java/com/wgzhao/addax/admin/service/impl/TargetServiceWithHiveImpl.java)：实现了与 Hive 相关的目标表管理逻辑，包括表结构演化和动态表名支持。
- [TaskQueueManagerV2Impl](backend/src/main/java/com/wgzhao/addax/admin/service/impl/TaskQueueManagerV2Impl.java)：实现了基于数据库持久化队列和 Redis 仲裁的混合并发控制架构。
- [JobContentService](backend/src/main/java/com/wgzhao/addax/admin/service/JobContentService.java)：定义和实现采集任务文件的编排和更新，每天会自动更新采集任务文件
- [TableService](backend/src/main/java/com/wgzhao/addax/admin/service/TableService.java)：定义了表结构演化和版本控制的接口，确保采集表的结构能够适应不断变化的数据需求。
- [TaskSchedulerService](backend/src/main/java/com/wgzhao/addax/admin/service/TaskSchedulerService.java)：定义了调度服务的接口，负责管理采集任务的调度和执行，确保任务按照预定的时间和依赖关系正确执行。

## 会话压缩指令

在会话压缩时，需要保留以下关键信息:

1. 架构决策(永不压缩)
2. 修改的文件和关键更改
3. 当前验证状态（通过/失败命令）
4. 未解决的风险、待办事项、回滚备注
