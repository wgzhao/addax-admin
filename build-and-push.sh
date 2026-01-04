#!/bin/bash

# Addax Admin - Docker 镜像构建和推送脚本
# 使用方法: ./build-and-push.sh [version] [registry]
# 例如: ./build-and-push.sh 1.0.0 wgzhao

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 默认参数
VERSION=${1:-latest}
REGISTRY=${2:-wgzhao}

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Addax Admin Docker 镜像构建和推送${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "版本: ${YELLOW}${VERSION}${NC}"
echo -e "仓库: ${YELLOW}${REGISTRY}${NC}"
echo ""

# 定义镜像名称
BACKEND_IMAGE="${REGISTRY}/addax-admin-backend:${VERSION}"
FRONTEND_IMAGE="${REGISTRY}/addax-admin-frontend:${VERSION}"

# 检查 Docker 是否运行
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}错误: Docker 未运行，请先启动 Docker${NC}"
    exit 1
fi

# 1. 构建后端镜像
echo -e "${GREEN}[1/4] 构建后端镜像...${NC}"
docker build \
    -t ${BACKEND_IMAGE} \
    -f backend/Dockerfile \
    .

echo -e "${GREEN}✓ 后端镜像构建完成: ${BACKEND_IMAGE}${NC}"
echo ""

# 2. 构建前端镜像
echo -e "${GREEN}[2/4] 构建前端镜像...${NC}"
docker build \
    -t ${FRONTEND_IMAGE} \
    -f frontend/Dockerfile \
    frontend/

echo -e "${GREEN}✓ 前端镜像构建完成: ${FRONTEND_IMAGE}${NC}"
echo ""

# 3. 如果不是 latest，同时打上 latest 标签
if [ "${VERSION}" != "latest" ]; then
    echo -e "${GREEN}[3/4] 添加 latest 标签...${NC}"
    docker tag ${BACKEND_IMAGE} ${REGISTRY}/addax-admin-backend:latest
    docker tag ${FRONTEND_IMAGE} ${REGISTRY}/addax-admin-frontend:latest
    echo -e "${GREEN}✓ latest 标签添加完成${NC}"
    echo ""
else
    echo -e "${YELLOW}[3/4] 跳过（已经是 latest 版本）${NC}"
    echo ""
fi

# 4. 推送镜像到 Docker Hub
echo -e "${GREEN}[4/4] 推送镜像到 Docker Hub...${NC}"
echo -e "${YELLOW}提示: 请确保已登录 Docker Hub (docker login)${NC}"
echo ""

read -p "是否继续推送镜像? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}取消推送${NC}"
    exit 0
fi

# 推送带版本号的镜像
echo -e "${GREEN}推送后端镜像 ${VERSION}...${NC}"
docker push ${BACKEND_IMAGE}

echo -e "${GREEN}推送前端镜像 ${VERSION}...${NC}"
docker push ${FRONTEND_IMAGE}

# 如果不是 latest，也推送 latest 标签
if [ "${VERSION}" != "latest" ]; then
    echo -e "${GREEN}推送 latest 标签...${NC}"
    docker push ${REGISTRY}/addax-admin-backend:latest
    docker push ${REGISTRY}/addax-admin-frontend:latest
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ 所有镜像推送完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "镜像列表:"
echo -e "  - ${YELLOW}${BACKEND_IMAGE}${NC}"
echo -e "  - ${YELLOW}${FRONTEND_IMAGE}${NC}"
if [ "${VERSION}" != "latest" ]; then
    echo -e "  - ${YELLOW}${REGISTRY}/addax-admin-backend:latest${NC}"
    echo -e "  - ${YELLOW}${REGISTRY}/addax-admin-frontend:latest${NC}"
fi
echo ""
echo -e "用户可以使用以下命令运行:"
echo -e "${YELLOW}export VERSION=${VERSION}${NC}"
echo -e "${YELLOW}export DOCKER_REGISTRY=${REGISTRY}${NC}"
echo -e "${YELLOW}docker-compose up -d${NC}"
echo ""
