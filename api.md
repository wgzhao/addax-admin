# API 变更记录与接口说明

本文件记录 TableController 和 TaskController 的接口路径变更、RESTful 规范后的新接口路径，以及每个接口的功能说明、参数说明和响应结构，供前端开发者参考。

---

## TableController（采集表管理）

| 原接口路径                  | HTTP方法 | 说明                   | 新接口路径（RESTful规范）                | 新HTTP方法 | 参数说明/响应结构/用途                |
|----------------------------|----------|------------------------|------------------------------------------|------------|--------------------------------------|
| /table                     | GET      | 分页查询采集表         | /api/v1/tables                           | GET        | query: page, pageSize, q, status, sortField, sortOrder；返回分页对象 Page<VwEtlTableWithSource> |
| /table/{tid}               | GET      | 查询单个采集表         | /api/v1/tables/{tableId}                 | GET        | path: tableId；返回 VwEtlTableWithSource |
| /table/{tid}               | DELETE   | 删除采集表             | /api/v1/tables/{tableId}                 | DELETE     | path: tableId；返回 204，无内容         |
| /table/fieldCompare/{tid}  | GET      | 查询表字段对比         | /api/v1/tables/{tableId}/columns         | GET        | path: tableId；返回 List<EtlColumn>     |
| /table/addaxResult/{tid}   | GET      | 查询表采集统计         | /api/v1/tables/{tableId}/statistics      | GET        | path: tableId；返回 List<EtlStatistic>  |
| /table/sourceSystem        | GET      | 查询所有源系统         | /api/v1/sources                          | GET        | 无参数；返回 List<EtlSource>            |
| /table/dbSources           | POST     | 查询采集源下所有数据库 | /api/v1/sources/{sourceId}/databases     | GET        | path: sourceId；返回 List<String>       |
| /table/tables              | POST     | 查询未采集的表         | /api/v1/sources/{sourceId}/databases/{dbName}/tables/uncollected | GET | path: sourceId, dbName；返回 List<String> |
| /table/batchSave           | POST     | 批量保存采集表         | /api/v1/tables/batch                     | POST       | body: List<EtlTable>；返回 int（数量）   |
| /table/save                | POST     | 新增单个采集表         | /api/v1/tables                           | POST       | body: EtlTable；返回 EtlTable           |
| /table/startEtl            | POST     | 启动采集               | /api/v1/tables/{tableId}/start           | POST       | path: tableId；body: 启动参数；返回字符串|
| /table/updateSchema        | POST     | 更新表结构             | /api/v1/tables/update-schema             | POST       | body: 更新参数；返回字符串               |
| /table/batchUpdateStatus   | POST     | 批量更新表状态         | /api/v1/tables/batch/status              | POST       | body: 状态参数；返回 int（数量）         |
| /table/view                | GET      | 查询表视图             | /api/v1/tables/view                      | GET        | query: 视图参数；返回集合                |
| /table/addaxJob/{tid}      | GET      | 获取Addax Job模板      | /api/v1/tables/{tableId}/addax-job       | GET        | path: tableId；返回字符串                |

---

## TaskController（采集任务管理）

| 原接口路径         | HTTP方法 | 说明             | 新接口路径（RESTful规范）         | 新HTTP方法 | 参数说明/响应结构/用途                |
|-------------------|----------|------------------|-----------------------------------|------------|--------------------------------------|
| /etl/start        | POST     | 启动采集任务计划 | /api/v1/tasks/start               | POST       | 无参数；返回字符串/状态码             |
| /etl/status       | GET      | 获取队列状态     | /api/v1/queue/status              | GET        | 无参数；返回 Map<String,Object>       |
| /etl/stop         | POST     | 停止队列监控     | /api/v1/queue/stop                | POST       | 无参数；返回字符串/状态码             |
| /etl/restart      | POST     | 重启队列监控     | /api/v1/queue/restart             | POST       | 无参数；返回字符串/状态码             |
| /etl/reset        | POST     | 重置队列         | /api/v1/queue/reset               | POST       | 无参数；返回字符串/状态码             |
| /etl/updateJob    | POST     | 立即更新所有任务 | /api/v1/tasks/update-job          | POST       | 无参数；返回字符串/状态码             |
| /etl/updateJob/{tid} | POST  | 立即更新单任务   | /api/v1/tasks/{taskId}/update-job | POST       | path: taskId；返回字符串/状态码        |
| /etl/execute/{tid}| POST     | 执行采集任务     | /api/v1/tasks/{taskId}/execute    | POST       | path: taskId；返回 Map/状态码          | 

---

## ParamController（参数配置管理）

| 路径 | HTTP方法 | 说明 | 参数说明/响应结构 |
|------|----------|------|------------------|
| /api/v1/dicts | GET | 查询所有字典 | 返回 List<SysDict> |
| /api/v1/dicts | POST | 新建字典 | body: SysDict，返回 201 + SysDict |
| /api/v1/dicts/{dictCode} | GET | 查询单个字典 | path: dictCode，返回 SysDict |
| /api/v1/dicts/{dictCode} | PUT | 更新字典 | path: dictCode, body: SysDict，返回 SysDict |
| /api/v1/dicts/{dictCode} | DELETE | 删除字典 | path: dictCode，返回 204，无内容 |
| /api/v1/dicts/{dictCode}/items | GET | 查询某字典下所有项 | path: dictCode，返回 List<SysItem> |
| /api/v1/dicts/{dictCode}/items | POST | 新建字典项 | path: dictCode, body: SysItem，返回 201 + SysItem |
| /api/v1/dicts/{dictCode}/items/{itemKey} | GET | 查询单个字典项 | path: dictCode, itemKey，返回 SysItem |
| /api/v1/dicts/{dictCode}/items/{itemKey} | PUT | 更新字典项 | path: dictCode, itemKey, body: SysItem，返回 SysItem |
| /api/v1/dicts/{dictCode}/items/{itemKey} | DELETE | 删除字典项 | path: dictCode, itemKey，返回 204，无内容 |

---

## SourceController（数据源配置管理）

| 路径 | HTTP方法 | 说明 | 参数说明/响应结构 | 请求示例 | 响应示例 |
|------|----------|------|------------------|----------|----------|
| /sources | GET | 查询所有数据源 | 返回 List<EtlSource> | 无 | [ { "id": 1, "name": "test", ... } ] |
| /sources | POST | 新建数据源 | body: EtlSource | { "name": "test", "url": "jdbc:mysql://...", "username": "root", "pass": "123456" } | { "id": 1, "name": "test", ... } |
| /sources/{id} | GET | 查询单个数据源 | path: id，返回 EtlSource | 无 | { "id": 1, "name": "test", ... } |
| /sources/{id} | DELETE | 删除数据源 | path: id，返回 204，无内容 | 无 | 无内容，204 |
| /sources | PUT | 批量保存数据源 | body: List<EtlSource>，返回 int（数量） | [ { "name": "test1", ... }, { "name": "test2", ... } ] | 2 |
| /sources/testConnect | POST | 测试数据源连接 | body: {url, username, password}，返回 ApiResponse<Boolean> | { "url": "jdbc:mysql://...", "username": "root", "password": "123456" } | { "code": 0, "data": true, "msg": "success" } |
| /sources/checkCode/{code} | GET | 检查编号是否存在 | path: code，返回 ApiResponse<Boolean> | 无 | { "code": 0, "data": false, "msg": "success" } |
| /sources/{sourceId}/databases | GET | 查询采集源下所有数据库 | path: sourceId，返回 List<String>（数据库名） | 无 | [ "db1", "db2" ] |
| /sources/{sourceId}/databases/{dbName}/tables/uncollected | GET | 查询未采集的表 | path: sourceId, dbName，返回 List<String>（表名） | 无 | [ "table1", "table2" ] |

**说明：**
- 所有接口均采用 RESTful 设计，资源名用复数，子资源用嵌套，方法语义清晰。
- 成功时直接返回资源对象或集合，失败时返回 { code, message } 错误结构和合适的 HTTP 状态码。
- 详细参数、响应体结构、分页/过滤等细节请参考后端接口文档或 Swagger。

如有疑问请联系后端开发者。
