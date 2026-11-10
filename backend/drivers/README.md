# JDBC 驱动目录

此目录用于存放自定义的 JDBC 驱动程序。

平台启动时把整个目录放在**隔离类加载器**里加载，不会加入应用主类路径。因此任何 JDBC 4 驱动 jar
（包括自带 SLF4J/Tomcat 的 HDP/Cloudera Hive standalone jar）都可以直接放进来，不会导致应用启动失败；
某个驱动损坏时只影响使用它的连接。

## 使用方法

1. 将你需要的 JDBC 驱动 JAR 文件放入此目录（jar 需包含 `META-INF/services/java.sql.Driver`）
2. 重启后端服务，驱动会自动加载

## 常用 JDBC 驱动下载

### MySQL
```bash
wget https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar
```

### PostgreSQL
```bash
wget https://jdbc.postgresql.org/download/postgresql-42.7.1.jar
```

### Oracle
下载地址：https://www.oracle.com/database/technologies/jdbc-ucp-downloads.html

### SQL Server
```bash
wget https://repo1.maven.org/maven2/com/microsoft/sqlserver/mssql-jdbc/12.4.2.jre11/mssql-jdbc-12.4.2.jre11.jar
```

### Hive
```bash
wget https://repo1.maven.org/maven2/org/apache/hive/hive-jdbc/3.1.3/hive-jdbc-3.1.3.jar
```

Hive JDBC 驱动只供 addax-admin 建表、读取元数据和创建分区使用；Addax 自身的 `hdfswriter` 不需要它。
普通 `hive-jdbc*.jar` 放进本目录并重启即可。

注意：如果驱动 jar 不在本目录（例如放在其他路径），可以在目标端的「驱动 jar 路径」中单独指定；
该 jar 同样使用独立类加载器加载。

### ClickHouse
```bash
wget https://repo1.maven.org/maven2/com/clickhouse/clickhouse-jdbc/0.5.0/clickhouse-jdbc-0.5.0-all.jar
```

## 注意事项

- 确保驱动版本与你的数据库版本兼容
- 某些驱动可能需要依赖其他 JAR 文件，请一并放入此目录
- Docker 部署时，此目录会被挂载为容器卷，可以在宿主机直接管理驱动文件
