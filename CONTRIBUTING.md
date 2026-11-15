# 贡献指南

感谢参与 Addax Admin。本文说明本地开发、提交规范与 PR 要求，提交前请先阅读。

## 开始之前

- 先搜索现有 Issue 与 Pull Request，避免重复工作。
- 较大的功能或架构改动请先开 Issue 讨论，说明使用场景与方案取舍。
- 安全问题不要公开提交，按 [SECURITY.md](SECURITY.md) 私下报告。

## 本地开发

环境要求：Java 21、Maven 3.9+、Node.js 22、Yarn 1.22、PostgreSQL、Redis，以及可用于联调的 Addax 安装目录。

```bash
# 后端（默认 50601）
yarn build:backend
# 或开发模式
yarn dev:backend

# 前端（默认 3030，指向本地后端）
VITE_API_HOST=http://localhost:50601 yarn dev

# 质量检查
yarn lint
yarn type-check
```

完整的数据库初始化、Addax 引擎安装与配置说明见 [安装与使用手册](docs/manual/README.md)。

## 分支与提交

- 从 `master` 切出分支：`feat/<topic>` 或 `fix/<topic>`，例如 `feat/ods-reconcile`。
- 提交信息遵循 Conventional Commits：`type(scope): subject`。
  - type：`feat`、`fix`、`refactor`、`perf`、`docs`、`test`、`chore`、`ci`
  - scope：`fe`、`be`、`api`、`db`、`infra`、`repo`、`deps`
  - subject 使用英文祈使句、首字母小写、不加句号
- 单个提交只做一件逻辑上完整的事；前后端改动尽量拆成独立提交。

## Pull Request

- 描述变更动机、核心改动、验证方式与影响范围；破坏性变更需注明迁移步骤。
- 提交前至少完成相关构建与测试，并在 PR 中写明验证结果。
- 行为变更请同步更新文档、截图或 `CHANGELOG.md`。
- 保持 PR 聚焦，礼貌回应评审意见。

## 代码约定

- 代码注释使用英文，注释解释「为什么」而不是「怎么做」。
- 遵循仓库现有分层与模块边界，不引入无关重构。
- 新增功能先明确语义与边界；涉及核心概念时同步更新 `CONTEXT.md`，涉及架构决策时补充 `docs/adr/`。
