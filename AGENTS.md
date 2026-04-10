# AGENTS.md

该文件旨在为 AI 提供关于该项目的开发和维护的指导原则和最佳实践。请遵循以下准则，以确保代码质量、可维护性和团队协作的顺利进行。

## 通用偏好

- 用中文回复，代码注释用英文，注释写 why 不写 how
- 简洁直接，不要多余总结和解释
- 直接写代码，不需要每次确认后再生成

## 项目概述

该项目为 [Addax](https://github.com/wgzhao/addax) 提供了管理界面和调度服务，在该管理工具的支持下，可以快速批量完成采集表的创建、修改和调度。核心功能包括：

- 采集表管理：支持表结构演化、动态表名和版本控制
- 调度服务：基于数据库持久化队列和 Redis 仲裁的混合并发控制架构
- 任务编排：支持采集任务文件的编排和更新，确保任务依赖关系正确处理

## 禁止

- 随意修改 `.env` 文件, `application.properties` 文件，使用环境变量方式临时覆盖配置
- 随意移除功能标志而不搜索所有调用点
- 提交代码前不运行测试

## 技术栈

- 前端: TypeScript, Vue 3 + Composition API, Vite, vuetifyjs, bun
- 后端: Java (JDK 21+), SpringBoot 3.5.6

## 编译

- 前端: `bun build:frontend`
- 后端: `bun build:backend`

## 本地测试流程

1. `bun build:backend` 编译后端服务
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

## 架构与设计宗旨

- 从第一性原理解构问题 一先明确什么是必须的，再决定怎么做
- 警惕 XY 问题-多角度审视方案，先确认真正要解决的是什么，主动提出替代方案
- 解決根本问题，不要 workaround -如果现有架构不支持，重构它
- 质疑不合理的需求和方向—发现问题立刻指出，不要等我问才说，不要奉承或无脑赞同
- 架构设计时参考 ddia-principles 和 software-design-philosophy 规则

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

## Git / PR 标准流程

当用户明确提出“提交并创建 PR”时，默认按以下流程执行（除非用户另有说明）：

1. 创建新分支后再提交，分支名建议使用 `feat/<topic>` 或 `fix/<topic>` 格式，根据本次修改的性质选择 `feat`（新功能）或 `fix`（修复）。例如：`feat/add-protobuf-dependency`。
2. 使用英文编写 commit message：
   - `title` 简洁明确（建议 Conventional Commits 风格）。
   - `description/body` 说明动机、核心改动、验证情况。
3. 提交前至少完成相关构建/测试（遵循本文件“编译”与“本地测试流程”）。
4. 使用 `gh` 命令创建 PR，不只推送分支：
   - 示例：`gh pr create --base master --head <branch> --title "<english title>" --body-file <file>`
5. PR 内容必须使用英文，并尽量完整包含：
   - Motivation / Background（为什么做）
   - What Changed（改了什么）
   - Design / Implementation Notes（实现与口径）
   - Validation（构建/测试结果）
   - Risks / Caveats / Follow-ups（注意事项、风险、后续）
6. 若无特别要求，PR 设为 Ready for review（非 Draft）。

以上流程可由一句 "提交并创建 PR" 触发，不需要用户重复描述细节格式要求。
