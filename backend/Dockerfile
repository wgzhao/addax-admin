# 构建阶段
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 复制父 pom.xml
COPY pom.xml /build/parent-pom.xml

# 复制后端源代码和 pom.xml
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src

# 修改 pom.xml 的 relativePath
RUN sed -i 's|<relativePath>../pom.xml</relativePath>|<relativePath>parent-pom.xml</relativePath>|' pom.xml

# 构建项目（跳过测试）
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 安装必要的工具（busybox-extras 包含 nc 命令）
RUN apk add --no-cache busybox-extras

# 从构建阶段复制 JAR 文件
COPY --from=builder /build/target/*.jar app.jar

# 创建必要的目录
RUN mkdir -p /app/logs /app/drivers /app/config /app/job

# 暴露端口
EXPOSE 50601

# 健康检查 - 检查端口是否监听
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD nc -z localhost 50601 || exit 1

# 运行应用
# 使用 PropertiesLauncher 支持外部 drivers 目录加载
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dloader.path=/app/drivers", \
  "-Dloader.main=com.wgzhao.addax.admin.AdminApplication", \
  "-cp", "app.jar", \
  "org.springframework.boot.loader.launch.PropertiesLauncher"]
