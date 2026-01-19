# 构建阶段
FROM node:22-alpine AS builder

WORKDIR /build

# 复制 package.json 和 package-lock.json
COPY package*.json ./

# 安装依赖
RUN yarn install

# 复制源代码
COPY . .

# 构建生产版本
RUN yarn build

# 生产阶段
FROM nginx:1.27-alpine

# 复制构建产物到 nginx
COPY --from=builder /build/dist /usr/share/nginx/html

# 复制 nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1

# 启动 nginx
CMD ["nginx", "-g", "daemon off;"]
