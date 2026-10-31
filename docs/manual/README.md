# Addax Admin 安装与使用手册

本手册面向两类读者：

- **部署者**：负责准备数据库、Redis、Addax 引擎和 Addax Admin 服务。
- **使用者**：负责维护采集源、目标端、采集表和日常调度。

管理界面右上角用户菜单中的“帮助中心”只收录使用与运维章节；安装章节保留在 GitHub 文档中，因为能打开管理界面时，系统通常已经完成安装。

## 推荐阅读路径

### 第一次部署

选择一种部署模式：

- [Docker 部署](install/01-docker.md)
- [主机模式部署](install/02-source.md#主机模式部署)

然后继续：

1. [安装 Addax 引擎](install/01-docker.md#安装-addax采集引擎必需)
2. [系统配置](install/03-config.md)
3. [快速上手](usage/01-quick-start.md)

### 第一次配置采集

1. [核心概念](usage/02-concepts.md)
2. [采集源与目标端](usage/03-sources-targets.md)
3. [批量创建采集表](usage/04-create-collections.md)
4. [目标表结构与字段映射](usage/05-target-contract.md)

### 日常运维

1. [调度与并发](usage/09-scheduling.md)
2. [执行监控与日志](operations/01-monitoring.md)
3. [故障排查](operations/02-troubleshooting.md)
4. [常见问题](operations/03-faq.md)

## 目录

### 安装部署

| 章节                                       | 内容                                           |
| ------------------------------------------ | ---------------------------------------------- |
| [Docker 部署](install/01-docker.md)        | 预构建镜像、源码构建、Addax 引擎安装、备份恢复 |
| [主机模式与本地开发](install/02-source.md) | 编译产物部署、systemd、Nginx、本地开发         |
| [系统配置](install/03-config.md)           | 环境变量、运行参数、HiveServer2、作业模板      |
| [升级与迁移](install/04-upgrade.md)        | 版本升级、v4.2.0 `target_options` 迁移、回滚   |

### 使用指南

| 章节                                                | 内容                                                  |
| --------------------------------------------------- | ----------------------------------------------------- |
| [快速上手](usage/01-quick-start.md)                 | 登录、建源、建目标、批量建表、验证                    |
| [核心概念](usage/02-concepts.md)                    | 采集路由、采集定义、执行快照、退役与停采              |
| [采集源与目标端](usage/03-sources-targets.md)       | 连接配置、调度日历、目标端类型与默认目标              |
| [批量创建采集表](usage/04-create-collections.md)    | 五步流程、目标策略、逐表预览、创建结果                |
| [目标表结构与字段映射](usage/05-target-contract.md) | `MANAGED`/`EXTERNAL`、字段映射、阻断与告警            |
| [目标端能力与表属性](usage/06-target-engines.md)    | Hive/关系型目标、写入语义、`target_options`、补数限制 |
| [动态表名](usage/07-dynamic-tables.md)              | `${biz_*}`/`${curr_*}` 变量、日表/月表示例、跨日风险  |
| [增量与过滤](usage/08-filtering.md)                 | 业务日期过滤、字段条件、`__max__<field>`、风险回退    |
| [调度与并发](usage/09-scheduling.md)                | 调度触发、队列、Redis 选主与分发、多节点权重、状态码  |

### 运维排障

| 章节                                          | 内容                                           |
| --------------------------------------------- | ---------------------------------------------- |
| [执行监控与日志](operations/01-monitoring.md) | 任务中心、采集状态、执行快照、采集日志         |
| [故障排查](operations/02-troubleshooting.md)  | 部署、数据库、Redis、Addax、目标契约、调度问题 |
| [常见问题](operations/03-faq.md)              | 停采/退役/恢复、重复数据、补数、字段映射只读等 |

## 术语

手册中的“采集定义”“采集路由”“执行快照”“目标契约”“停采”“退役”等术语以仓库根目录的 [CONTEXT.md](../../CONTEXT.md) 为准。
