# Addax Admin

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.2-green.svg)](https://spring.io/projects/spring-boot)

Addax Admin 是一个现代化的 ETL 管理后端服务，为 [Addax](https://github.com/wgzhao/addax) ETL 工具提供完整的 Web 管理界面支持。

## 📋 项目概述

这是一个完整的 ETL 管理解决方案的后端服务，整个解决方案由三个项目组成：

- **[Addax](https://github.com/wgzhao/addax)** - ETL 核心程序
- **[Addax Admin](https://github.com/wgzhao/addax-admin)** - ETL 管理后端服务
- **[Addax UI](https://github.com/wgzhao/addax-ui)** - ETL 管理前端界面

## ✨ 主要特性

- 🚀 **现代化架构** - 基于 Spring Boot 3.x 和 Java 21
- 🔐 **安全认证** - 集成 JWT 令牌认证和 Spring Security
- 💾 **多数据库支持** - 支持 PostgreSQL、Oracle、SQL Server 等
- 📊 **RESTful API** - 提供完整的 REST API 接口
- 🔧 **灵活配置** - 支持多环境配置和动态参数
- 📈 **监控管理** - ETL 作业监控和状态管理
- 🎯 **高性能** - Redis 缓存支持，提升响应速度

## 🛠 技术栈

### 核心框架
- **Spring Boot 3.2.2** - 应用框架
- **Spring Security** - 安全框架
- **Spring Data JPA** - 数据访问层
- **Hibernate 6.6.11** - ORM 框架

### 数据库支持
- **PostgreSQL** - 主要数据库
- **Redis** - 缓存和会话存储

### 工具库
- **Lombok** - 代码生成工具
- **HuTool** - Java 工具包
- **Apache Commons Lang3** - 通用工具库
- **JWT** - JSON Web Token 认证

## 🚀 快速开始

### 环境要求

- **Java 21** 或更高版本
- **Maven 3.6+**
- **PostgreSQL 12+** (推荐)
- **Redis 6.0+** (可选，用于缓存)

### 安装步骤

1. **克隆项目**
```bash
git clone https://github.com/wgzhao/addax-admin.git
cd addax-admin
```

2. **配置数据库**
   
   创建 PostgreSQL 数据库并执行初始化脚本：
```bash
psql -U postgres -d your_database -f src/main/resources/schema.sql
psql -U postgres -d your_database -f src/main/resources/data.sql
```

3. **配置应用**
   
   编辑 `src/main/resources/application-dev.properties`：
```properties
# 数据库配置
spring.datasource.url=jdbc:postgresql://localhost:5432/addax_admin
spring.datasource.username=your_username
spring.datasource.password=your_password

# Redis 配置 (可选)
spring.redis.host=localhost
spring.redis.port=6379
```

4. **构建并运行**
```bash
mvn clean package
java -jar target/addax-admin-0.0.1-SNAPSHOT.jar
```

应用将在 `http://localhost:9090/api/v1` 启动。

### Docker 部署

```bash
# 构建镜像
docker build -t addax-admin .

# 运行容器
docker run -p 9090:9090 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/addax \
  -e SPRING_DATASOURCE_USERNAME=username \
  -e SPRING_DATASOURCE_PASSWORD=password \
  addax-admin
```

## 🔧 配置说明

### 环境配置

项目支持多环境配置：

- `application.properties` - 基础配置
- `application-dev.properties` - 开发环境
- `application-prod.properties` - 生产环境

### 数据库函数和存储过程

项目包含针对不同数据库的函数和存储过程：

- `src/main/resources/functions/` - 数据库函数
- `src/main/resources/procedures/` - 存储过程

支持 Oracle 和 PostgreSQL 两种数据库。

## 📝 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。


## 🙏 致谢

感谢 [IntelliJ IDEA](https://jetbrains.com) 为本项目提供开发工具的支持！

