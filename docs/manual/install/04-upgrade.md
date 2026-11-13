# 升级与迁移

升级前先完成数据库备份和版本记录。数据库结构由服务启动时的幂等迁移维护，但版本回退仍需要恢复备份。

## 升级前检查

1. 记录当前版本：

   ```bash
   docker compose images app
   ```

2. 备份数据库：

   ```bash
   docker compose exec postgres pg_dump -U addax_admin addax_admin > backup-$(date +%F).sql
   ```

3. 确认当前没有大量任务正在排队或执行，避免升级窗口内任务状态难以解释。
4. 阅读目标版本的 Release Notes，确认配置项和数据库变更。

## Docker 升级

拉取镜像方式：

```bash
docker compose pull
docker compose up -d
```

源码构建方式：

```bash
git pull
docker compose up -d --build
```

升级后检查：

```bash
docker compose ps
docker compose logs -f app
```

确认前端可以登录、采集表列表可以打开、调度和任务中心正常。

## 源码升级

```bash
git pull
cd backend
mvn clean package
```

然后按现有部署方式替换 `lib/addax-admin-<version>.jar` 并重启服务。前端需要重新构建：

```bash
cd frontend
yarn install
yarn build
```

## v4.5.0 升级说明

### 数据库变更

首次启动会执行一次幂等迁移，自动创建或补齐以下对象，无需手工执行 SQL：

- 新建 `alert_channel`、`alert_rule`、`alert_event`、`alert_delivery`、`alert_silence`；
- 新建 `user_source_grant`；
- `users` 增加 `auth_version`、`display_name`、`email`、`remark`、`created_at`、`last_login_at`、`password_changed_at`；
- `etl_source` 增加 `connect_config`，`url` 放宽到 1000；
- `etl_column` 增加 `source_meta`，字段映射索引改为大小写敏感；
- `etl_execution` 增加 `source_id`，`etl_table` 的标识字段放宽。

迁移失败会中止启动，处理方式与下方「回滚」一致。

### 告警配置

`WECOM_ROBOT_KEY` 和 `alert.wechat.*` 不再读取，外部告警统一在「系统管理 → 告警配置」维护：

1. 升级前设置 `ALERT_SECRET_KEY`，用于加密通道中的 Webhook、签名密钥和 SMTP 密码；未设置时会从
   `JWT_SECRET` 派生并在日志中告警。
2. 升级后新增通道、测试发送成功后再启用，并按事件类型配置规则。
3. 未启用任何通道时事件记录为 `SKIPPED_NO_CHANNEL`，不会回退到环境变量。

密钥变更后已保存的通道配置无法解密，需要重新填写。

### 账号与权限

- 角色固定为 `admin`、`user`、`guest`；`guest` 是登录后的只读角色，不是匿名访问。
- 升级后非管理员账号仍为只读，需要管理员在「账号管理 → 授权」中为 `user` 账号逐个采集源授予
  「可操作」（`OPERATE`）。`admin` 和 `guest` 不接受授权。
- `user` 只能修改、执行和补数被授权的采集源；`guest` 不能查看采集日志和作业内容。
- 角色变更、停用账号和修改密码会提升 `auth_version`，旧 JWT 在下一次请求时立即失效。
- 详见 [账号与权限](../usage/13-access-control.md)。

### 采集源标识与日期变量

- 调度任务 id 改为 `source-<id>`，源级统计按 `sid` 分组。
- `etl_source.code` 已移除：启动迁移会删除该列和 `vw_etl_table_with_source` 中的对应字段，
  `/sources/check-code` 接口与表单中的“采集编号”一并删除。采集源以 `id` 和 `name` 标识；
  `etl_execution.source_code` 仅作为历史快照保留，新执行记录不再写入。
  **该迁移不可回滚到旧版本**：升级时所有节点必须同时停止后统一部署，不支持新旧版本混跑。
- `curr_*` 变量改为作业生成时冻结，与 `biz_*` 一样写入作业 JSON。历史模板中的占位符会在分发时
  兜底解析，下一次切日刷新后持久化为具体值；依赖每次运行取当前时间的作业需要调整。

## v4.2.0 及之后的 `target_options` 迁移

从 v4.2.0 升级时无需手工改表。服务启动会自动执行一次幂等迁移：

1. 把 `etl_table` 的 `part_kind`、`part_name`、`part_format`、`storage_format`、`compress_format` 回填到 `target_options`。
2. 重建 `vw_etl_table_with_source` 视图。
3. 删除旧的 5 个列。

迁移完成后，目标端专属属性统一存放在 `etl_table.target_options`（jsonb）。全新安装直接以 `scripts/schema.sql` 为准，不需要执行额外迁移。

如果升级日志中出现迁移失败：

- 停止服务，保留日志。
- 从备份恢复数据库。
- 检查数据库用户是否有 `ALTER TABLE`、`CREATE OR REPLACE VIEW` 权限。
- 修复后重新启动，迁移会再次尝试。

## HiveServer2 / HDFS 配置迁移（目标端化）

从旧版本升级时，服务启动会把全局 `HIVE_SERVER2`、`HDFS_PREFIX`、`HDFS_STORAGE_FORMAT`、
`HDFS_COMPRESS_FORMAT`、`HDFS_CONFIG` 以及 `wH` 模板中的 `defaultFS`/`hdfsSitePath` 合并进每个
HDFS 目标端的 `connect_config`：

- 只补缺失键，目标端已有的配置不会被覆盖；
- 旧 `sys_item` 行保留，但系统配置接口和页面不再展示；
- 目标端缺少 `writer_template_key` 时补 `wH`。

**驱动**：迁移会把旧 `HIVE_SERVER2.driverPath` 一并保留到 HDFS 目标端，因此升级后 Hive 操作通常
可以继续使用原来的 standalone jar。新目标端建议把普通 `hive-jdbc*.jar` 放到 `${APP_HOME}/drivers`
（Docker 为 `/app/drivers`）；如果驱动 jar 打包了自己的 Tomcat/SLF4J、无法放入 `drivers/`，在目标端的
「驱动 jar 路径」里单独指定即可。驱动缺失时接口返回 400 并提示驱动目录。

升级后检查：

1. 目标端管理里每个 HDFS 目标端都有 HiveServer2 地址、`defaultFS` 和 HDFS 前缀；
2. 点击「测试连接」返回成功；
3. 执行一次表结构刷新，确认建表和分区正常。

## 回滚


1. 停止应用：

   ```bash
   docker compose stop app
   ```

2. 切回上一个镜像版本或上一份代码。
3. 恢复升级前的数据库备份：

   ```bash
   docker compose exec -T postgres psql -U addax_admin addax_admin < backup-2026-10-31.sql
   ```

4. 启动应用并检查日志。

> 数据库迁移是向前执行的。如果新版本已经写过数据，直接回退代码而不恢复数据库可能造成字段或视图不匹配，因此回滚应同时恢复数据库备份。

## 相关文档

- [Docker 部署](01-docker.md)
- [系统配置](03-config.md)
- [故障排查](../operations/02-troubleshooting.md)
