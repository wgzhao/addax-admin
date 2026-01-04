# Docker 镜像发布指南

本文档说明如何构建、推送 Docker 镜像到 Docker Hub，以及用户如何使用预构建镜像。

## 📦 镜像仓库

- **Docker Hub 仓库**: https://hub.docker.com/u/wgzhao
- **后端镜像**: `wgzhao/addax-admin-backend`
- **前端镜像**: `wgzhao/addax-admin-frontend`

## 🏗️ 构建和推送镜像（维护者）

### 前提条件

1. **登录 Docker Hub**
   ```bash
   docker login
   # 输入用户名和密码
   ```

2. **确保代码已提交**
   ```bash
   git status
   git add .
   git commit -m "Release version X.X.X"
   git tag vX.X.X
   git push origin main --tags
   ```

### 方法 1: 使用自动化脚本（推荐）

```bash
# 赋予执行权限
chmod +x build-and-push.sh

# 构建并推送 latest 版本
./build-and-push.sh

# 构建并推送指定版本（例如 1.0.0）
./build-and-push.sh 1.0.0

# 指定其他 Docker Hub 用户名
./build-and-push.sh 1.0.0 yourusername
```

脚本会自动：
1. ✅ 构建后端镜像
2. ✅ 构建前端镜像
3. ✅ 添加版本标签和 latest 标签
4. ✅ 推送到 Docker Hub

### 方法 2: 手动构建和推送

```bash
# 设置版本号
export VERSION=1.0.0
export REGISTRY=wgzhao

# 构建后端镜像
docker build -t ${REGISTRY}/addax-admin-backend:${VERSION} -f backend/Dockerfile .
docker tag ${REGISTRY}/addax-admin-backend:${VERSION} ${REGISTRY}/addax-admin-backend:latest

# 构建前端镜像
docker build -t ${REGISTRY}/addax-admin-frontend:${VERSION} -f frontend/Dockerfile frontend/
docker tag ${REGISTRY}/addax-admin-frontend:${VERSION} ${REGISTRY}/addax-admin-frontend:latest

# 推送镜像
docker push ${REGISTRY}/addax-admin-backend:${VERSION}
docker push ${REGISTRY}/addax-admin-backend:latest
docker push ${REGISTRY}/addax-admin-frontend:${VERSION}
docker push ${REGISTRY}/addax-admin-frontend:latest
```

### 验证镜像

```bash
# 查看本地镜像
docker images | grep addax-admin

# 拉取镜像测试
docker pull wgzhao/addax-admin-backend:latest
docker pull wgzhao/addax-admin-frontend:latest

# 检查镜像信息
docker inspect wgzhao/addax-admin-backend:latest
```

## 🚀 使用预构建镜像（用户）

用户无需编译，直接使用预构建的镜像即可运行。

### 快速开始

1. **准备部署文件**
   ```bash
   # 下载必要文件
   wget https://raw.githubusercontent.com/wgzhao/addax-admin/main/docker-compose.prod.yml
   wget https://raw.githubusercontent.com/wgzhao/addax-admin/main/.env.example
   
   # 下载数据库初始化脚本
   mkdir -p scripts
   wget -P scripts/ https://raw.githubusercontent.com/wgzhao/addax-admin/main/scripts/schema.sql
   wget -P scripts/ https://raw.githubusercontent.com/wgzhao/addax-admin/main/scripts/data.sql
   ```

2. **配置环境变量**
   ```bash
   # 复制并编辑配置文件
   cp .env.example .env
   vim .env
   ```

3. **启动服务**
   ```bash
   # 使用生产配置启动
   docker-compose -f docker-compose.prod.yml up -d
   
   # 或者使用默认配置（也支持预构建镜像）
   docker-compose up -d
   ```

4. **访问应用**
   - 前端: http://localhost
   - 后端 API: http://localhost:50601/api/v1

### 指定版本

```bash
# 使用特定版本
export VERSION=1.0.0
export DOCKER_REGISTRY=wgzhao
docker-compose -f docker-compose.prod.yml up -d

# 或者在 .env 文件中设置
echo "VERSION=1.0.0" >> .env
echo "DOCKER_REGISTRY=wgzhao" >> .env
docker-compose -f docker-compose.prod.yml up -d
```

### 最小化部署

如果只想快速体验，只需要这些文件：
```
.
├── docker-compose.prod.yml  # 或 docker-compose.yml
├── .env                      # 可选，使用默认配置
└── scripts/
    ├── schema.sql
    └── data.sql
```

## 🔄 更新镜像

### 用户更新到最新版本

```bash
# 拉取最新镜像
docker-compose -f docker-compose.prod.yml pull

# 重启服务
docker-compose -f docker-compose.prod.yml up -d

# 查看新版本运行状态
docker-compose -f docker-compose.prod.yml ps
```

### 用户更新到指定版本

```bash
# 修改 .env 文件中的版本号
echo "VERSION=1.1.0" > .env

# 拉取并重启
docker-compose -f docker-compose.prod.yml pull
docker-compose -f docker-compose.prod.yml up -d
```

## 📊 镜像大小优化

当前镜像大小（参考）：
- 后端镜像: ~200-300 MB（基于 eclipse-temurin:21-jre-alpine）
- 前端镜像: ~50-80 MB（基于 nginx:1.27-alpine）

优化建议：
1. ✅ 使用 Alpine Linux 基础镜像
2. ✅ 多阶段构建，分离构建和运行环境
3. ✅ .dockerignore 排除不必要的文件
4. ✅ 仅包含运行时依赖

## 🔐 私有镜像仓库

如果使用私有 Docker Registry：

### 推送到私有仓库

```bash
# 构建并推送到私有仓库
./build-and-push.sh 1.0.0 registry.example.com/addax
```

### 从私有仓库拉取

```bash
# 登录私有仓库
docker login registry.example.com

# 设置仓库地址
export DOCKER_REGISTRY=registry.example.com/addax

# 启动服务
docker-compose -f docker-compose.prod.yml up -d
```

## 🏷️ 版本管理策略

建议的版本标签策略：

- `latest`: 最新稳定版本
- `1.0.0`: 具体版本号（语义化版本）
- `1.0`: 次版本号（自动更新补丁版本）
- `1`: 主版本号（自动更新次版本）
- `develop`: 开发版本（不稳定）

示例：
```bash
# 发布 1.2.3 版本
./build-and-push.sh 1.2.3

# 同时打上其他标签
docker tag wgzhao/addax-admin-backend:1.2.3 wgzhao/addax-admin-backend:1.2
docker tag wgzhao/addax-admin-backend:1.2.3 wgzhao/addax-admin-backend:1
docker push wgzhao/addax-admin-backend:1.2
docker push wgzhao/addax-admin-backend:1
```

## 🧪 CI/CD 集成

### GitHub Actions 示例

创建 `.github/workflows/docker-publish.yml`：

```yaml
name: Build and Push Docker Images

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v2
      
      - name: Login to Docker Hub
        uses: docker/login-action@v2
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}
      
      - name: Extract version
        id: version
        run: echo "VERSION=${GITHUB_REF#refs/tags/v}" >> $GITHUB_OUTPUT
      
      - name: Build and push backend
        uses: docker/build-push-action@v4
        with:
          context: .
          file: backend/Dockerfile
          push: true
          tags: |
            wgzhao/addax-admin-backend:${{ steps.version.outputs.VERSION }}
            wgzhao/addax-admin-backend:latest
      
      - name: Build and push frontend
        uses: docker/build-push-action@v4
        with:
          context: frontend
          file: frontend/Dockerfile
          push: true
          tags: |
            wgzhao/addax-admin-frontend:${{ steps.version.outputs.VERSION }}
            wgzhao/addax-admin-frontend:latest
```

## 📝 发布检查清单

发布新版本前的检查项：

- [ ] 代码已测试通过
- [ ] 版本号已更新（pom.xml, package.json）
- [ ] CHANGELOG 已更新
- [ ] Git 标签已创建
- [ ] 本地构建测试成功
- [ ] 镜像已推送到 Docker Hub
- [ ] 文档已更新
- [ ] 发布说明已准备

## 🆘 故障排查

### 推送失败

```bash
# 检查登录状态
docker login

# 检查镜像是否存在
docker images | grep addax-admin

# 手动推送测试
docker push wgzhao/addax-admin-backend:latest
```

### 镜像拉取失败

```bash
# 检查镜像是否存在
docker pull wgzhao/addax-admin-backend:latest

# 检查网络连接
curl -I https://hub.docker.com

# 使用镜像加速器（中国大陆用户）
# 编辑 /etc/docker/daemon.json
{
  "registry-mirrors": [
    "https://mirror.ccs.tencentyun.com"
  ]
}
```

## 📚 相关资源

- [Docker Hub](https://hub.docker.com/)
- [Docker 官方文档](https://docs.docker.com/)
- [Docker Compose 文档](https://docs.docker.com/compose/)
- [最佳实践](https://docs.docker.com/develop/dev-best-practices/)

---

**享受容器化部署的便利！** 🐳
