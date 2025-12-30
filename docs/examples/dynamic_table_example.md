# Dynamic Table Name Example

示例说明如何配置按日切分的动态表名（例如 my_table_{yyyyMMdd}）。

1. 在任务配置（AddaxJob）中选择“动态表名”选项。
2. 填写模板：my_table_{yyyyMMdd}
3. 指定日期范围：例如 2025-01-01 至 2025-01-07，系统将解析并列出以下表名：
   - my_table_20250101
   - my_table_20250102
   - ...
   - my_table_20250107
4. 提交后，系统会为每个存在的表创建对应的采集任务或在调度周期内动态触发。

注意：
- 模板日期格式应遵循 Java DateTime pattern，例如 yyyyMMdd、yyyyMM 等。
- 确保时区一致，避免跨时区解析错误。