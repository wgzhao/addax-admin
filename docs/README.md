# 文档

本目录是 Addax Admin 的文档入口。所有面向部署者和使用者的说明都以 [`manual/`](manual/README.md) 为唯一事实源；README 只保留项目概览、亮点和最短部署路径。

## 手册

- [安装与使用手册](manual/README.md)
  - [安装部署](manual/install/01-docker.md)
  - [使用指南](manual/usage/01-quick-start.md)
  - [运维排障](manual/operations/01-monitoring.md)

## 设计与实现

- [领域术语与边界](../CONTEXT.md)
- [ADR 0001：退役采集定义与执行快照](adr/0001-retire-collection-definitions-with-execution-snapshots.md)
- [ADR 0002：外部目标契约与字段映射](adr/0002-external-target-contract-and-column-mapping.md)
- [ADR 0003：一条队列记录对应一个 Addax 进程](adr/0003-one-addax-process-per-queue-row.md)
- [ADR 0004：采集身份的目标端范围与退役](adr/0004-collection-identity-target-scope-and-retirement.md)

## 安全与测试

- [安全审计记录](security-audit.md)
- [采集状态矩阵](testing/collection-state-matrix.md)

## 维护约定

- README 不复制手册正文，只保留概览、亮点、最短部署步骤和文档链接。
- 安装章节面向部署者，不进入管理界面的帮助中心。
- 管理界面帮助中心只渲染 `manual/usage/` 和 `manual/operations/`，与 GitHub 使用同一份 Markdown。
- 新增或移动章节时，同步更新本页和 [`manual/README.md`](manual/README.md) 的目录。
