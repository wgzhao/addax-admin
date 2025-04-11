# Oracle 到 PostgreSQL 存储过程迁移说明

## 迁移概述

本项目将 Oracle 数据库中的存储过程和函数迁移到 PostgreSQL 数据库。迁移过程中主要涉及以下几个方面的转换：

1. 语法差异处理
2. 数据类型映射
3. 函数替换
4. 系统函数替换
5. 特定 Oracle 功能的重新实现

## 主要转换对照表

### 数据类型转换

| Oracle 类型 | PostgreSQL 类型 |
|------------|----------------|
| VARCHAR2   | VARCHAR        |
| NUMBER     | NUMERIC        |
| NUMBER(p,s)| NUMERIC(p,s)   |
| DATE       | TIMESTAMP      |
| CLOB       | TEXT           |
| BLOB       | BYTEA          |

### 函数替换

| Oracle 函数 | PostgreSQL 函数 |
|------------|----------------|
| NVL        | COALESCE       |
| DECODE     | CASE WHEN      |
| SYSDATE    | CURRENT_TIMESTAMP |
| TO_CHAR    | TO_CHAR (注意格式可能不同) |
| ROWID      | CTID           |
| WM_CONCAT  | STRING_AGG     |
| LISTAGG    | STRING_AGG     |
| XMLAGG     | STRING_AGG     |

### 语法差异

| Oracle 语法 | PostgreSQL 语法 |
|------------|----------------|
| `CREATE OR REPLACE PROCEDURE name(param IN type) AS` | `CREATE OR REPLACE FUNCTION name(param type) RETURNS void AS $$` |
| `BEGIN ... END;` | `BEGIN ... END; $$ LANGUAGE plpgsql` |
| `SELECT ... INTO ... FROM DUAL;` | `SELECT ... INTO ...;` |
| `EXECUTE IMMEDIATE` | `EXECUTE` |
| `chr(10)` | `E'\n'` |
| `instr(str1, str2)` | `position(str2 IN str1)` |
| `MINUS` | `EXCEPT` |

## 特别注意事项

1. **ROWID 替换**：Oracle 的 ROWID 在 PostgreSQL 中使用 CTID 替代，但它们的行为并不完全相同。CTID 是物理位置标识符，会在表更新时变化。

2. **字符串连接**：Oracle 使用 `||` 进行字符串连接，PostgreSQL 也支持这种方式。

3. **换行符**：Oracle 使用 `chr(10)` 表示换行符，PostgreSQL 使用 `E'\n'`。

4. **聚合函数**：Oracle 的 `wm_concat` 和 `listagg` 在 PostgreSQL 中都使用 `string_agg` 替代。

5. **执行动态 SQL**：Oracle 的 `EXECUTE IMMEDIATE` 在 PostgreSQL 中使用 `EXECUTE`。

6. **异常处理**：两种数据库的异常处理机制有所不同，需要相应调整。

## 已迁移的存储过程

- `sp_imp_alone`：数据采集和处理的主要存储过程

## 待迁移的存储过程和函数

- 其他相关的存储过程和函数需要按照类似的规则进行迁移

## 迁移后的测试

迁移完成后，建议进行以下测试：

1. 单元测试：验证每个存储过程和函数的功能是否正常
2. 集成测试：验证存储过程之间的调用关系是否正常
3. 性能测试：比较迁移前后的性能差异

## 注意事项

- PostgreSQL 中的存储过程实际上是返回 void 的函数
- 某些 Oracle 特有的功能可能需要重新设计才能在 PostgreSQL 中实现
- 注意数据类型的精度和范围差异