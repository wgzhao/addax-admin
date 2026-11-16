# 主机部署（发行包）

发行包是 Release 附件中的 `addax-admin-<version>-host.tar.gz`，包含预编译的后端 jar、前端静态文件和部署脚本，与 Docker 镜像在同一发布流程中构建。适用于已有 Java 21、PostgreSQL、Redis 和 Nginx、但不需要构建工具链的主机。

如果需要二次开发或跟随 `master` 分支，见 [源码构建与本地开发](03-source.md)。

## 包内容

```text
addax-admin-<version>/
├── BUILD-INFO          # 版本、Git 提交、构建时间
├── backend/            # 后端，目录布局与 /opt/app/addax-admin 一致
│   ├── bin/service.sh
│   ├── lib/addax-admin-<version>.jar
│   ├── config/application.properties
│   ├── config/env.sh          # 唯一需要修改的配置文件
│   ├── drivers/               # 自定义 JDBC 驱动
│   ├── job/                   # 采集任务文件（运行期生成）
│   └── logs/
├── frontend/           # 前端静态文件（Nginx root 指向这里）
└── deploy/
    ├── README.md
    ├── install.sh
    ├── nginx.conf.example
    ├── addax-admin.service
    ├── sql/            # 可选：给 DBA 预建库用
    └── CHECKSUMS.sha256
```

## 环境要求

- Java 21 运行时（JRE 即可）
- PostgreSQL 15+
- Redis 6+
- Nginx
- `nc`（netcat，`service.sh` 启动检查使用；只用 systemd 时不需要）
- `rsync`（`install.sh` 部署前端时使用）
- Addax 采集引擎（运行时必需，程序包见 [Addax Releases](https://github.com/wgzhao/addax/releases)，安装说明见 [安装 Addax](01-docker.md#安装-addax采集引擎必需)）

## 1. 下载与校验

从 GitHub Releases 下载对应版本的发行包和校验文件：

```bash
VERSION=4.6.0   # 替换为实际版本，不带 v 前缀
wget "https://github.com/wgzhao/addax-admin/releases/download/v${VERSION}/addax-admin-${VERSION}-host.tar.gz"
wget "https://github.com/wgzhao/addax-admin/releases/download/v${VERSION}/addax-admin-${VERSION}-host.tar.gz.sha256"
sha256sum -c "addax-admin-${VERSION}-host.tar.gz.sha256"
tar xzf "addax-admin-${VERSION}-host.tar.gz"
cd "addax-admin-${VERSION}"
sha256sum -c deploy/CHECKSUMS.sha256
```

国内网络可先配置代理或镜像源再下载。

## 2. 初始化数据库

```sql
CREATE USER addax_admin WITH PASSWORD '替换为实际密码';
CREATE DATABASE addax_admin OWNER addax_admin;
```

空库由应用首次启动时自动初始化；需要提前建表时，可手工执行包内 `deploy/sql/schema.sql` 与 `deploy/sql/data.sql`。

## 3. 安装

```bash
sudo sh deploy/install.sh
```

默认安装到 `/opt/app/addax-admin`，前端到 `/var/www/html/addax-admin-ui`。常用参数：

```bash
sudo sh deploy/install.sh --app-home /data/addax-admin --web-root /data/www/addax-admin
sudo sh deploy/install.sh --no-systemd      # 只用 bin/service.sh 管理进程
sudo sh deploy/install.sh --no-frontend     # 前后端分离部署，只装后端
```

脚本行为：

- 首次安装复制配置模板，并生成随机的 `JWT_SECRET` 与 `ALERT_SECRET_KEY`；
- 重复执行即为升级：替换 jar 和前端文件，不覆盖 `config/env.sh`、`config/application.properties`，不修改 `drivers/`、`job/`、`logs/`；
- 旧 jar 保留为 `lib/addax-admin-*.jar.before-<时间戳>`，回滚时改回文件名即可；
- 有 root 权限时创建 `addax` 用户、写入并启用 systemd 单元，但不会自动启动服务。

不使用 `install.sh` 时，把 `backend/` 整个目录放到目标路径，再按下面步骤配置和启动。

## 4. 配置后端

编辑 `/opt/app/addax-admin/config/env.sh`：

```bash
export DB_HOST="127.0.0.1"
export DB_PORT="5432"
export DB_NAME="addax_admin"
export DB_USERNAME="addax_admin"
export DB_PASSWORD="替换为实际密码"

export REDIS_HOST="127.0.0.1"
export REDIS_PORT="6379"
export REDIS_PASSWORD=""
export REDIS_DB="0"

export WEIGHT="1.0"
```

- `JWT_SECRET`、`ALERT_SECRET_KEY` 由 `install.sh` 首次安装时生成，不要随意修改：前者会使所有登录失效，后者会使已保存的告警通道密钥无法解密。
- `LOG_DIR` 默认跟随应用目录，一般无需修改。
- 后端端口默认 `50601`，可用 `export SERVER_PORT=...` 覆盖。
- 完整变量说明见 [系统配置](04-config.md)。

## 5. 启动与停止

systemd（`install.sh` 已安装并启用，但不会启动）：

```bash
sudo systemctl start addax-admin
sudo systemctl status addax-admin
sudo journalctl -u addax-admin -f
```

或使用 `service.sh`（二选一，不要同时启用）：

```bash
sudo -u addax /opt/app/addax-admin/bin/service.sh start
sudo -u addax /opt/app/addax-admin/bin/service.sh status
sudo -u addax /opt/app/addax-admin/bin/service.sh stop
tail -f /opt/app/addax-admin/logs/addax-admin.log
```

## 6. 配置 Nginx

```bash
sudo cp deploy/nginx.conf.example /etc/nginx/conf.d/addax-admin.conf
sudo vi /etc/nginx/conf.d/addax-admin.conf   # 修改 server_name、root、proxy_pass
sudo nginx -t && sudo systemctl reload nginx
```

前端使用相对路径 `/api/v1` 访问后端；前后端分开部署时，把 `proxy_pass` 改成后端主机地址。`frontend/` 是完整静态站点，升级时必须整体替换（`install.sh` 使用 `rsync --delete`）。

## 7. 首次登录与 Addax 引擎

1. 打开 `http://<host>/`，使用 `admin` / `admin123` 登录并立即修改密码。
2. 进入「系统配置」，把「Addax 程序目录」设置为后端进程可访问的 Addax 安装路径（例如 `/opt/app/addax`）。
3. 需要自定义 JDBC 驱动时，把驱动 jar 放入 `/opt/app/addax-admin/drivers/` 后重启后端；目录内 README 提供常用驱动下载地址。
4. 按 [快速上手](../usage/01-quick-start.md) 创建第一个采集表。

## 8. 升级与回滚

```bash
sha256sum -c addax-admin-<new-version>-host.tar.gz.sha256
tar xzf addax-admin-<new-version>-host.tar.gz
cd addax-admin-<new-version>
sudo sh deploy/install.sh
sudo systemctl restart addax-admin
```

升级前建议备份数据库，具体见 [升级与迁移](05-upgrade.md)。回滚时把 `lib/` 下的 `*.jar.before-<时间戳>` 改回 `addax-admin-<old-version>.jar` 并重启；如果新版本已执行数据库迁移，需要同时恢复备份。

## 9. 多节点部署

Addax Admin 采用 sharing-nothing 架构：多个节点连接同一套 PostgreSQL 与 Redis，通过 Redis 选主和心跳协调调度。每个节点重复上述安装步骤，使用不同的 `--app-home` / `--web-root`（或只装后端，多个后端共用一个 Nginx），并通过 `WEIGHT` 设置该节点的并发负载比例。详见 [调度与并发](../usage/09-scheduling.md)。

## 10. 排障

常见问题见 [故障排查](../operations/02-troubleshooting.md)。与发行包相关的典型现象：

| 现象 | 排查 |
| --- | --- |
| `Java 21+ required` | `java -version` 不是 21；在 `config/env.sh` 设置 `JAVA_HOME` 与 `PATH` |
| `service.sh start` 停在 `Checking port` | 查看 `logs/addax-admin.log`，多为数据库密码或 Redis 连接错误 |
| 页面打开但接口 502 | 后端未启动或端口不一致；检查 `SERVER_PORT` 与 Nginx `proxy_pass` |
| 前端 404 / 白屏 | Nginx `root` 未指向安装后的前端目录，或升级时未整体替换 |
| 告警通道密钥无法解密 | `ALERT_SECRET_KEY` 被修改；改回原值或重新保存通道配置 |

## 相关文档

- [Docker 部署](01-docker.md)
- [源码构建与本地开发](03-source.md)
- [系统配置](04-config.md)
- [升级与迁移](05-upgrade.md)
