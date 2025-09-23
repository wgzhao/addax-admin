请将合适版本的 JDBC driver jar 文件放入本目录。
建议根据数据库类型和版本选择对应的 jar。
示例：postgresql-42.6.0.jar
#!/bin/sh
# 环境变量模板，复制为 env.sh 并根据实际情况修改
export DB_URL="jdbc:postgresql://localhost:5432/addax_admin"
export DB_USERNAME="addax_admin"
export DB_PASSWORD="addax_admin@123"
export DB_DRIVER="org.postgresql.Driver"
# 其他可选环境变量

