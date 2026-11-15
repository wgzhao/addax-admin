# ADR-0013: 数据库初始化归应用所有，初始化脚本随构建产物分发

- 状态：Accepted
- 日期：2026-11-15

## 背景

Docker 部署一直由 `postgres` 容器执行宿主机挂载的 `scripts/schema.sql`、`scripts/data.sql`
（`docker-entrypoint-initdb.d`）。也就是说，**建库的组件是 postgres 镜像 + 宿主机文件，升级数据库的组件是应用镜像**，
两者版本可以不同。

Issue #125 就是这个错配的具体后果：用户按文档从 `master` 下载了已移除 `etl_source.code` 的 `schema.sql`，
而 `latest` 镜像还是 v4.5.0，反序列化视图时仍引用该列，启动直接失败：

```
ERROR: column s.code does not exist
create view public.vw_etl_table_with_source as ...
```

即使文档改成固定 Release tag，这种耦合依然存在：只要宿主机目录里的 SQL 与应用镜像来自不同 revision，就会再次发生。
"建库"和"升级"必须由同一个可版本化的产物负责。

## 决策

1. **空库初始化并入 `SchemaInitializer`。** 启动迁移在拿到 advisory lock 后先判断 `public` schema 是否为空；
   为空则在同一个事务里执行 jar 内置的 `schema.sql` 和 `data.sql`，再走原有的升级流水线。初始化失败整事务回滚，
   数据库保持为空，下次启动重试；多个实例并发启动由同一把锁串行化，只有一个实例真正建库。

2. **脚本只有一份，构建时打进 jar。** 权威副本仍是仓库根的 `scripts/schema.sql`、`scripts/data.sql`
   （手工 `psql` 初始化、code review 都继续用它们）。`backend/pom.xml` 把它们作为 classpath 资源打包，
   `Dockerfile` 把这两个文件复制进构建上下文；不在 `backend/src/main/resources` 下再复制一份，避免双真相源。

3. **`docker-compose.yml` 不再挂载初始化脚本。** 数据库由应用镜像自己创建，postgres 容器只负责运行实例。
   旧 compose 里的 `docker-entrypoint-initdb.d` 挂载只在数据卷为空时生效，对已有部署无害，可以保留。

4. **执行完脚本后 `reset all`。** `schema.sql` 是 pg_dump 产物，会设置 `search_path=''`、关闭超时等会话级参数；
   这些参数会随连接回到 Hikari 池，破坏后续未限定 schema 的语句。初始化结束（或失败）后恢复会话默认值。
   `data.sql` 里未限定的种子函数调用因此需要显式 `set search_path = public`。

5. **JPA 依赖初始化器。** 字典模板（`RdbmsTemplateInitializer`）和配置引导（`SystemConfigService`）在各自的
   `@PostConstruct` 里查表，而 JPA 只依赖 DataSource，不依赖初始化器。首次空库启动时它们会先执行并静默失败
   （`relation "sys_item" does not exist`，作业模板整轮丢失）。通过 Spring Boot 的
   `EntityManagerFactoryDependsOnPostProcessor` 让 `entityManagerFactory` 依赖 `SchemaInitializer`
   （与 Flyway/Liquibase 相同的钩子）。多个节点并发首次启动时种子插入还会撞主键，`DictService.ensureItem`
   按幂等处理该冲突。

## 影响

- 空库首次启动自动建表并写入默认数据，Docker 与主机模式的初始化路径一致，不再依赖宿主机放置 SQL 文件。
- 已有数据库完全不受影响：仍是原有升级流水线向前迁移，迁移逻辑与视图指纹机制不变。
- 手工用 `psql -f scripts/schema.sql` 初始化仍然可用；应用会识别出非空库并跳过初始化。
- 镜像版本与数据库结构绑定；升级数据库不再需要同步更新宿主机文件。

## 验证

- `scripts/e2e/empty-database-init-e2e.sh`：空库上并发启动两个实例，只有一个执行初始化；校验表结构、
  种子数据、视图指纹、登录与新增采集源 API；重启不重复初始化、不重复写种子行。
- `scripts/e2e/schema-upgrade-e2e.sh --state fresh`：以当前 `scripts/schema.sql` 建库后升级路径行为不变。
