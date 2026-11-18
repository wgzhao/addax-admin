# Addax Admin 主机部署（发行包）

本包包含运行 Addax Admin 所需的全部编译产物，**不需要 Maven、Node.js 或 Yarn**。适用于已有
Java 21、PostgreSQL、Redis 和 Nginx 的主机。

```text
.
├── BUILD-INFO          # 版本、Git 提交、构建时间
├── LICENSE
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
    ├── install.sh
    ├── nginx.conf.example
    ├── addax-admin.service
    ├── sql/            # 可选：给 DBA 预建库用
    └── CHECKSUMS.sha256
```

## 1. 环境要求

- Java 21 运行时（JRE 即可）
- PostgreSQL 15+
- Redis 6+
- Nginx
- `nc`（netcat，`bin/service.sh` 启动检查使用；使用 systemd 时不需要）
- `rsync`（使用 `install.sh` 部署前端时需要）
- Addax 采集引擎（运行时必需，程序包从 https://github.com/wgzhao/addax/releases 下载）

## 2. 校验与解压

```bash
sha256sum -c addax-admin-<version>-host.tar.gz.sha256
tar xzf addax-admin-<version>-host.tar.gz
cd addax-admin-<version>
sha256sum -c deploy/CHECKSUMS.sha256
```

## 3. 安装

默认安装到 `/opt/app/addax-admin`，前端到 `/var/www/html/addax-admin-ui`：

```bash
sudo sh deploy/install.sh
```

常用参数：

```bash
sudo sh deploy/install.sh --app-home /data/addax-admin --web-root /data/www/addax-admin
sudo sh deploy/install.sh --user hive       # 指定服务与 Addax 作业的运行账号
sudo sh deploy/install.sh --no-systemd      # 只用 bin/service.sh 管理进程
sudo sh deploy/install.sh --no-frontend     # 前后端分离部署，只装后端
```

脚本行为：

- 首次安装会复制配置模板，并生成随机的 `JWT_SECRET` 与 `ALERT_SECRET_KEY`；
- 重复执行即为升级：替换 jar 与前端文件，**不覆盖** `config/env.sh`、`config/application.properties`，
  **不修改** `drivers/`、`job/`、`logs/`；
- 旧 jar 保留为 `lib/addax-admin-*.jar.before-<时间戳>`，回滚时改回文件名即可；
- 有 root 权限时处理运行账号、写入并启用 systemd 单元，但不会自动启动服务。

### 运行账号与 HDFS 权限

`--user`（默认 `addax`）决定后端进程、`bin/service.sh` 和它拉起的 `addax.sh` 采集作业以哪个
系统账号运行。安装输出会明确打印 `service user`；账号已存在时直接复用，不存在时创建系统账号并打印
醒目提示。

这一步不是可选项而是权限前提：很多 HDFS 环境只允许 `hive` 账号写目标目录。如果服务以 `addax`
运行，采集作业也会以 `addax` 写 HDFS，运行期就会出现权限不足。安装前先确认目标端要求的账号：

```bash
sudo sh deploy/install.sh --user hive
```

- 账号已存在：复用，不修改其 shell、home 或组；
- 账号不存在：以系统账号（`nologin`）创建，并在安装输出中提醒该账号将执行所有采集作业；
- 升级时同样可以传 `--user` 更换账号：脚本会重新 `chown` 应用目录、前端目录并重写 systemd 单元；
- 非 root 安装会忽略 `--user`，服务以执行安装的当前账号运行，且不安装 systemd 单元。

选定账号后，该账号还必须对 Addax 程序目录可写（见第 7 节）；`drivers/`、`job/`、`logs/` 归该账号所有。

### 手工安装

不使用 `install.sh` 时，把 `backend/` 整个目录放到目标路径，再按第 5、6 节配置和启动即可。

## 4. 配置数据库与 Redis

```sql
CREATE USER addax_admin WITH PASSWORD '替换为实际密码';
CREATE DATABASE addax_admin OWNER addax_admin;
```

数据库为空时，后端首次启动会自动执行内置的 `schema.sql` 和 `data.sql`；需要由 DBA 提前建表时，
可以手工执行 `deploy/sql/schema.sql` 与 `deploy/sql/data.sql`。

编辑 `config/env.sh`：

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

- `JWT_SECRET`、`ALERT_SECRET_KEY` 由 `install.sh` 首次安装时生成，不要随意修改：前者会使所有登录失效，
  后者会使已保存的告警通道密钥无法解密。
- `LOG_DIR` 默认跟随应用目录，一般无需修改。
- 后端端口默认 `50601`，可用 `export SERVER_PORT=...` 覆盖。

## 5. 启动与停止

使用 systemd（`install.sh` 已安装并启用）：

```bash
sudo systemctl start addax-admin
sudo systemctl status addax-admin
sudo journalctl -u addax-admin -f
```

或使用脚本（二选一，不要同时启用）。把 `<service-user>` 换成安装时选定的账号（默认 `addax`）：

```bash
sudo -u <service-user> /opt/app/addax-admin/bin/service.sh start
sudo -u <service-user> /opt/app/addax-admin/bin/service.sh status
sudo -u <service-user> /opt/app/addax-admin/bin/service.sh stop
tail -f /opt/app/addax-admin/logs/addax-admin.log
```

## 6. 配置 Nginx

```bash
sudo cp deploy/nginx.conf.example /etc/nginx/conf.d/addax-admin.conf
sudo vi /etc/nginx/conf.d/addax-admin.conf   # 修改 server_name、root、proxy_pass
sudo nginx -t && sudo systemctl reload nginx
```

前端使用相对路径 `/api/v1` 访问后端；前后端分开部署时，把 `proxy_pass` 改成后端主机地址。
`frontend/` 里的文件名带 hash，升级时必须整体替换（`install.sh` 使用 `rsync --delete`）。

## 7. 首次登录与 Addax 引擎

1. 打开 `http://<host>/`，使用 `admin` / `admin123` 登录并立即修改密码。
2. 进入「系统配置」，把「Addax 程序目录」设置为后端进程可访问的 Addax 安装路径
   （例如 `/opt/app/addax`）。
3. 确认服务账号（安装输出中的 `service user`）对 Addax 目录（`bin/addax.sh`、`log/`）可写，
   并设置 `conf/core.json` 的 `core.server.address`；采集作业以该账号写目标端，HDFS 权限不足时
   需要改用有权限的账号重新安装或在 HDFS 侧授权。
4. 需要自定义 JDBC 驱动时，把驱动 jar 放入 `/opt/app/addax-admin/drivers/` 后重启后端；
   目录内 README 提供常用驱动下载地址。
5. 按管理界面提示配置采集源、目标端，开始创建采集表。

## 8. 升级

```bash
sha256sum -c addax-admin-<new-version>-host.tar.gz.sha256
tar xzf addax-admin-<new-version>-host.tar.gz
cd addax-admin-<new-version>
sudo sh deploy/install.sh
sudo systemctl restart addax-admin
```

升级前建议备份数据库。数据库结构由服务启动时的幂等迁移维护，回滚版本时需要恢复备份。

升级默认沿用已安装的 `config/env.sh` 与运行账号。需要更换账号时，在重复执行 `install.sh` 时加
`--user <account>`，脚本会重新设置目录属主并重写 systemd 单元，然后重启服务。

## 9. 多节点部署

Addax Admin 采用 sharing-nothing 架构：多个节点连接同一套 PostgreSQL 与 Redis，通过 Redis 选主和
心跳协调调度。每个节点重复执行本包安装步骤，使用不同的 `--app-home` / `--web-root`
（或只装后端，多个后端共用一个 Nginx），并通过 `WEIGHT` 设置该节点的并发负载比例。

## 10. 排障

| 现象 | 排查 |
| --- | --- |
| 启动脚本报 `Java 21+ required` | `java -version` 不是 21；在 `config/env.sh` 设置 `JAVA_HOME` 与 `PATH` |
| `service.sh start` 停在 `Checking port` | 查看 `logs/addax-admin.log`，多为数据库密码或 Redis 连接错误 |
| 启动脚本提示找不到 `nc` | 安装 netcat，或改用 systemd |
| 页面打开但接口 502 | 后端未启动或端口不一致；检查 `SERVER_PORT` 与 Nginx `proxy_pass` |
| 前端 404 / 白屏 | `root` 是否指向 `frontend/` 内容所在目录，且升级时已整体替换 |
| 数据库迁移失败 | 查看日志中的 SQL 错误；结构变更前先备份数据库 |
| 采集写 HDFS 报权限不足 | 服务账号（`service user`）没有目标路径写权限；用 `--user hive`（或实际有权限的账号）重装，或在 HDFS 侧授权 |
| 告警通道密钥无法解密 | `ALERT_SECRET_KEY` 被修改；改回原值或重新保存通道配置 |
