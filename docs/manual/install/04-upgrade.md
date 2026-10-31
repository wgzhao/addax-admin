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
