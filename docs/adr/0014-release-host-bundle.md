# ADR-0014：发布主机部署发行包（host bundle）

## 状态

已采纳（2026-11-16）

## 上下文

发布流程只在 tag 推送时构建 Docker 镜像。没有 Docker、也不想装 Maven/Node/Yarn 的主机用户只能按
「主机模式」文档从源码构建，产物版本、目录布局和配置模板都靠人工拼装：

- `backend/pom.xml` 里有一个 `maven-assembly-plugin`，产出的 zip 本身是坏的（`target/` 未 clean 时把旧 jar
  一起打包、描述符引用的 `bin/service.sh` / `env.template.sh` 路径不存在），也从不包含前端；
- `scripts/addax-admin.service` 是过期模板（`addax-admin-0.0.1-SNAPSHOT.jar`、`/opt/addax-admin`）；
- 主机文档要求复制 `application.properties`，而该文件默认 `spring.jpa.show-sql=true`，生产会刷日志；
- 运维人员无法从制品判断「这台机器跑的到底是哪个版本」。

## 决策

1. **Release 同时发布一个主机部署发行包** `addax-admin-<version>-host.tar.gz`（同时提供 `.sha256`），
   与 Docker 镜像由同一个 tag 触发构建。
2. **包内三分区**：`backend/` 按 `/opt/app/addax-admin` 布局组织；`frontend/` 是 `frontend/dist` 的内容；
   `deploy/` 放部署说明、`install.sh`、Nginx 与 systemd 示例、可选的初始化 SQL。包根带 `BUILD-INFO`
   （版本、commit、构建时间），`deploy/CHECKSUMS.sha256` 覆盖 jar 与前端入口。
3. **打包由独立脚本完成**：`scripts/package-host-bundle.sh` 只接受已构建的 jar 和 dist，校验
   `frontend/package.json`、`pom.xml`、jar 文件名三者与参数版本一致后打包。退役
   `maven-assembly-plugin`（它无法包含前端，且会复现旧 jar 混入的问题）。
4. **配置单一来源**：包内 `backend/config/application.properties` 直接复制
   `backend/src/main/resources/application.properties`（默认 `show-sql=false`）；`config/env.sh` 来自
   `backend/config/env.template.sh`，是用户唯一需要修改的文件。
5. **`deploy/install.sh` 幂等**：首次安装复制配置并生成随机 `JWT_SECRET`/`ALERT_SECRET_KEY`；重复执行
   即升级——替换 jar 与前端（`rsync --delete`），不覆盖 `config/env.sh`、不修改 `drivers/`、`job/`、`logs/`，
   旧 jar 保留为 `.before-<时间戳>` 以便回滚。
6. **CI 双构建**：Docker job 与发行包 job 并行，各自从同一 tag 构建。没有合并为「构建一次、两边消费」，
   因为那需要额外的 `Dockerfile.release`，会让最终镜像阶段的配置出现两份；并行构建不增加墙钟时间。
7. **发布资产上传幂等**：发行包 job 在 release 不存在时创建 **draft**（不提前暴露未写完的 Release），
   存在时只 `--clobber` 覆盖资产；同时响应 `release: published`，覆盖「先手工建 Release 再推 tag」的顺序。
   双语 Release notes 仍由维护者人工写入后发布。

## 影响

- 主机用户获得与镜像同源的、版本可追溯的制品，无需构建工具链；
- `scripts/addax-admin.service` 被删除，systemd 模板统一到 `scripts/bundle/host/addax-admin.service`，
  它通过 `sh -c '. config/env.sh && exec java ...'` 加载配置（systemd 不解析 `env.sh` 里的 `export`）；
- `backend/src/main/resources/application.properties` 的 `show-sql` 默认改为 `false`，本地需要时用
  `SPRING_JPA_SHOW_SQL=true` 覆盖；
- 安装文档重排为 `01-docker` / `02-host` / `03-source` / `04-config` / `05-upgrade`；历史 tag 中的旧路径不受影响。

## 备选方案

- **只在 Maven 中打包**：无法构建前端，且 Maven 生命周期与发布 tag 的版本校验耦合更弱，否决。
- **同时发布 zip**：Linux 部署以 `tar.gz` 为主，zip 在 Windows 解压会丢失执行位，且新增一个需要长期维护的
  格式；有需求时可在打包脚本里加一条 `zip` 命令，不作为首版交付物。
- **构建一次供 Docker 与发行包共用**：需要维护第二个 Dockerfile，收益只是省下约 5 分钟 CI 时间，否决。
