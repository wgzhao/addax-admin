<p align="center">
  <img src="docs/assets/social-preview.png" alt="Addax Admin — 面向 Addax 离线引擎的 T+1 ODS 采集控制台" width="100%">
</p>

<p align="center">
  <strong>让数据采集像水电一样可靠</strong><br>
  面向 <a href="https://github.com/wgzhao/addax">Addax</a> 离线引擎的 T+1 ODS 采集控制台
</p>

<p align="center">
  简体中文 · <a href="README_en.md">English</a>
</p>

<p align="center">
  <a href="https://opensource.org/licenses/Apache-2.0"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <a href="https://github.com/wgzhao/addax-admin/releases"><img src="https://img.shields.io/github/v/release/wgzhao/addax-admin?sort=semver" alt="Release"></a>
  <a href="https://hub.docker.com/r/wgzhao/addax-admin"><img src="https://img.shields.io/docker/pulls/wgzhao/addax-admin?label=docker%20pulls" alt="Docker Pulls"></a>
  <img src="https://img.shields.io/badge/Java-21-orange.svg" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.1.2-green.svg" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue-3.x-4FC08D.svg" alt="Vue 3">
</p>

<p align="center">
  <a href="#快速开始">快速开始</a> ·
  <a href="docs/manual/README.md">文档</a> ·
  <a href="#界面预览">界面预览</a> ·
  <a href="docs/roadmap.md">路线图</a> ·
  <a href="https://github.com/wgzhao/addax-admin/releases">发布记录</a> ·
  <a href="https://hub.docker.com/r/wgzhao/addax-admin">Docker Hub</a>
</p>

---

## 产品定位

Addax Admin 是 Addax 离线 ETL 引擎的企业级管理与运维控制台，专注 **T+1 ODS 层数据落地**。它在同一个界面里完成采集定义、调度执行、补数回补、数据对账、告警与审计的完整闭环，帮助数据团队回答三个问题：今天的采集是否健康、数据是否落对、出了问题能否快速定位与追溯。

| 适合场景 | 明确不做 |
| --- | --- |
| T+1 离线采集与 ODS 落地 | 实时 CDC / 流式同步 |
| Hive、关系型数据库作为目标端 | 通用 ETL 转换（T 交给下游 Hive / dbt / Spark） |
| MongoDB、Elasticsearch 等异构源接入 | 通用 DAG 编排平台 |
| 需要多节点高可用、审计与源级授权的团队 | 通用数据质量平台 |

---

## 界面预览

![经营总览](docs/assets/screenshots/dashboard-overview.webp)

*经营总览：今日采集健康度、KPI、风险与待办、采集量趋势。*

![今日运行](docs/assets/screenshots/dashboard-today.webp)

*今日运行：按采集源展开的日切心跳与风险下钻。*

![采集表详情](docs/assets/screenshots/table-detail.webp)

*采集表详情：字段映射、执行快照、对账与失败记录集中在一处。*

<details>
<summary>更多界面截图</summary>

![Home - light and dark](docs/assets/screenshots/dashboard-overview-split.webp)
![Health](docs/assets/screenshots/dashboard-health.webp)
![Data Quality](docs/assets/screenshots/dashboard-quality.webp)
![SLA](docs/assets/screenshots/dashboard-sla.webp)
![Source Management](docs/assets/screenshots/source.webp)
![Target Management](docs/assets/screenshots/target.webp)
![Collection Tables](docs/assets/screenshots/ods-etl.webp)
![Field Mapping](docs/assets/screenshots/table-field.webp)
![Execution Snapshots](docs/assets/screenshots/table-execution.webp)
![Collection Result](docs/assets/screenshots/addax-result.webp)
![Addax Job](docs/assets/screenshots/addax-json.webp)
![Collection Log](docs/assets/screenshots/etl-log.webp)
![Security Audit](docs/assets/screenshots/audit.webp)
![Collection Calendar](docs/assets/screenshots/etl-calendar.webp)
![Type Mappings](docs/assets/screenshots/etl-type-mappings.webp)
![System Settings](docs/assets/screenshots/sys-setting.webp)

完整截图见 [docs/assets/screenshots/](docs/assets/screenshots/)。
</details>

---

## 核心能力

### 采集与建模

- **批量创建采集表**：UI 中几次点击即可批量新增数百到上千张采集表，并一键在目标端（Hive 或关系型数据库）同步创建表与字段。
- **目标契约与显式字段映射**：目标表支持平台托管（自动建表与结构演进）与外部管理（只读校验、不执行 DDL）两种模式；源字段与目标字段允许不同名，Reader 输出、Writer 列、DDL、执行快照和错误提示共用同一份一对一映射；批量创建逐表预览并隔离阻断与告警。
- **表结构演化**：自动探测源端字段类型变更、新增/删除字段并同步修改目标表结构；分区表提供历史数据兼容策略，最大限度减少中断与人工干预。
- **多源接入**：关系型数据库、MongoDB（集合采样推断字段、多库接入）与 Elasticsearch（索引/别名、mapping 与文档采样、Query DSL 过滤）。
- **动态表名**：原生支持日表、月表等模板（如 `t_order_${biz_date_short}`），自动解析业务日期并生成采集任务。

### 调度与运行

- **增量采集与智能过滤**：支持以上一次采集结果（如前一日最大 ID 或指定字段）作为增量条件，也支持自定义过滤表达式。
- **补数回补**：在采集表详情页按日历选择历史日期（支持多段区间和单日），异步逐日回补并实时查看批次进度；业务日期变量按目标日期注入，不影响正常调度。
- **混合并发控制架构**：DB 持久化队列 + Redis 选主与任务分发 + 数据库租约，兼顾可靠性、审计能力与故障恢复。
- **多节点并发与权重**：sharing-nothing 架构、Redis 选主与心跳、节点权重（`WEIGHT`），实现全局/源级并发控制、任务高可用与负载均衡。

### 可信与治理

- **数据对账**：核对 Addax 上报的读取行数与写入行数（写入 = 读取 − 错误行数），差异进入失败记录并触发告警。
- **字段级血缘**：按字段名全局搜索，查看字段从哪个源表来、落到哪张 ODS 表。
- **数据质量断言**：近 7 天非零均值为基线，识别空采与漏采，命中后走告警中心与错误中心。
- **执行快照**：采集启动时冻结定义、字段映射与上下文，后续配置修改不影响历史结论。
- **失败事实与错误中心**：记录失败阶段、可执行摘要与堆栈片段，按错误指纹聚合重复失败，日志过期后摘要仍然保留。
- **就绪信号外发**：采集完成后按需外发 `collection_complete` Webhook，驱动下游调度。

### 企业级能力

- **告警中心**：企业微信、钉钉、飞书、Webhook 与邮件（SMTP）通道在界面维护，支持测试发送、规则路由、投递重试、静默窗口和通道配置加密。
- **源级授权**：admin / user / guest 三角色，普通用户按采集源授予「可操作」权限；角色或密码变更立即使旧令牌失效。
- **安全审计**：登录、配置变更、权限拒绝均有审计记录，可导出 CSV。
- **可扩展**：JWT + Spring Security、Spring Data JPA、前端模块化设计，并提供客户白标配置（产品名、Logo、支持与文档链接）。

---

## 技术架构

| 层 | 技术栈 |
| --- | --- |
| 后端 | Java 21、Spring Boot 4.1、Spring Data JPA、Spring Security + JWT、PostgreSQL、Redis |
| 前端 | Vue 3、TypeScript、Vite、Vuetify、Pinia |
| 部署 | Docker Compose（应用 + PostgreSQL + Redis），或 Java 21 + Nginx 主机模式 |

```text
addax-admin/
├── backend/                 # Spring Boot 后端服务（API、调度、持久化、队列与租约）
├── frontend/                # Vue 3 + Vite + TypeScript + Vuetify 管理界面
├── docs/                    # 手册、ADR、测试与安全文档，以及 README 截图（assets/screenshots/）
├── scripts/                 # 部署与运维脚本（DB 初始化、systemd service 模板等）
└── screenshots/             # 本地 E2E 截图产物（不入库）
```

数据库结构由打包进后端 jar 的编号迁移链 [db/migration](backend/src/main/resources/db/migration)（`V0001__baseline.sql`、`V0002__seed_reference_data.sql` …）管理：空库首次启动按顺序执行，已有数据库按 `schema_migration` 记录增量升级。

---

## 快速开始

Addax Admin 只负责管理与调度，不执行实际采集，三种部署模式都需要单独准备 **[Addax 采集引擎](https://github.com/wgzhao/addax/releases)**（程序包可从上游 Releases 下载）。

### Docker 模式（推荐）

前置条件：Docker 20.10+、Docker Compose 2.0+、可访问的 Addax 安装目录。

```bash
# 1. 创建部署目录并下载部署文件
mkdir addax-admin && cd addax-admin
RELEASE_TAG=v4.7.0   # 替换为最新 Release 标签
wget "https://raw.githubusercontent.com/wgzhao/addax-admin/${RELEASE_TAG}/docker-compose.yml"
wget "https://raw.githubusercontent.com/wgzhao/addax-admin/${RELEASE_TAG}/.env.example"
cp .env.example .env

# 2. 拉取镜像并启动
export VERSION="${RELEASE_TAG#v}"
docker compose -f docker-compose.yml pull
docker compose -f docker-compose.yml up -d
```

访问 <http://localhost:50080>，默认账号 `admin`，密码 `admin123`，首次登录后请立即修改密码。

> 镜像仅在发布时构建，`latest` 指向最新 Release；`master` 分支仍在快速迭代，请勿用于生产。国内网络可改用 Quay 备用源：`echo "DOCKER_REGISTRY=quay.io/wgzhao" >> .env`。

### 主机部署（发行包）

适用于已有 Java 21、PostgreSQL、Redis 和 Nginx、但不需要构建工具链的环境。发行包内含预编译的后端 jar、前端静态文件和部署脚本，与 Docker 镜像在同一发布流程中构建。

```bash
# 1. 从 GitHub Releases 下载发行包并校验（VERSION 换成实际版本，不带 v）
VERSION=4.7.0
wget "https://github.com/wgzhao/addax-admin/releases/download/v${VERSION}/addax-admin-${VERSION}-host.tar.gz"
wget "https://github.com/wgzhao/addax-admin/releases/download/v${VERSION}/addax-admin-${VERSION}-host.tar.gz.sha256"
sha256sum -c "addax-admin-${VERSION}-host.tar.gz.sha256"
tar xzf "addax-admin-${VERSION}-host.tar.gz"

# 2. 安装到 /opt/app/addax-admin，前端到 /var/www/html/addax-admin-ui
#    --user 指定服务与 Addax 作业的运行账号；HDFS 只允许 hive 写入时用 --user hive
cd "addax-admin-${VERSION}"
sudo sh deploy/install.sh

# 3. 按提示修改 config/env.sh、确认输出中的 service user，再配置 Nginx 并启动
```

安装输出会打印 `service user`。该账号同时决定 `addax.sh` 采集作业写目标端时的权限，账号已存在时直接复用，不存在时创建并给出提示。

包内 `deploy/README.md` 是完整部署说明；仓库版本见 [主机部署（发行包）](docs/manual/install/02-host.md)。

### 从源代码部署

适用于需要二次开发或跟随 `master` 的场景，需要额外的 Maven、Node.js 和 Yarn。完整步骤见 [源码构建与本地开发](docs/manual/install/03-source.md#从源码构建部署)。

---

## 文档

- [安装与使用手册](docs/manual/README.md)
- [Docker 部署](docs/manual/install/01-docker.md)
- [主机部署（发行包）](docs/manual/install/02-host.md)
- [源码构建与本地开发](docs/manual/install/03-source.md)
- [快速上手](docs/manual/usage/01-quick-start.md)
- [目标表结构与字段映射](docs/manual/usage/05-target-contract.md)
- [目标端能力与表属性](docs/manual/usage/06-target-engines.md)
- [MongoDB 采集](docs/manual/usage/10-mongodb.md)
- [Elasticsearch 采集](docs/manual/usage/11-elasticsearch.md)
- [调度与并发](docs/manual/usage/09-scheduling.md)
- [补数回补](docs/manual/usage/12-fillback.md)
- [故障排查](docs/manual/operations/02-troubleshooting.md)
- [架构决策记录](docs/adr/)

---

## 参与贡献

欢迎提交 Issue 与 Pull Request。开始之前请阅读 [贡献指南](CONTRIBUTING.md)；安全问题请按 [SECURITY.md](SECURITY.md) 私下报告。

---

## 许可证

本项目基于 Apache License 2.0，详见 [LICENSE](LICENSE)。
