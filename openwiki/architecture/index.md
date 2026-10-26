# 文件

- [采集执行链路](execution-pipeline.md) - 从 Worker 接收队列任务到 Addax 进程退出、快照与统计落库、失败重试和用户取消的完整执行时序与失败语义。
- [调度与分布式队列](scheduling-and-queue.md) - 采集触发如何进入 PostgreSQL 持久化队列，以及 Redis 仲裁下的 Master/Worker 选举、心跳、加权分派、租约与故障恢复机制。
- [系统架构总览](system-overview.md) - Addax Admin 的运行时组件、单节点与多节点拓扑、端到端数据流、后端模块边界以及可扩展的接缝。
