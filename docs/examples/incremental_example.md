# Incremental Filter Example

这是一个示例，说明如何在任务配置中使用上次采集的最大 ID 作为本次增量过滤条件。

示例步骤：

1. 首先按照正常流程添加采集表
2. 进入采集表管理-> 点击详情
3. 过滤规则里，填写 `__max__id`，这里 `__max__` 是固定标签，后面接你希望增量的字段名称
4. 配置完成后，系统在生成模板时，会先通过 hive jdbc 使用 `select max(id) from table where logdate=<date>` 查询到最近一次采集数据的最大值，然后形成 `where id > max_id` 这样的过滤条件填写在采集模板中。