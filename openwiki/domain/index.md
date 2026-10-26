# 文件

- [采集定义与生命周期](collection-definition-lifecycle.md) - 采集定义、采集源、目标端与执行事实的关系，N/W/R/Y/E/U/X 状态语义，退役/恢复、停采、源停用与历史可见性规则。
- [元数据刷新与表结构演化](schema-evolution-and-metadata.md) - 源字段发现、字段变更检测、Hive/RDBMS 目标 DDL 演进规则、动态表名解析、风险日志与刷新失败时的状态转换。
- [目标契约与字段映射](target-contract-and-mapping.md) - MANAGED/EXTERNAL 目标结构所有权、显式 source→target 字段映射、契约校验项、指纹、阻断/告警语义与 target_options。
