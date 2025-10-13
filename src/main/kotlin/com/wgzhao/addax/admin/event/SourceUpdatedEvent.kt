package com.wgzhao.addax.admin.event

import org.springframework.context.ApplicationEvent

/**
 * 数据源更新事件，当数据源信息变更时发布此事件。
 * 监听此事件的组件可以根据需要进行相应的处理，例如更新相关作业配置等。
 */

class SourceUpdatedEvent @JvmOverloads constructor(
    source: Any,
    val sourceId: Int,
    val connectionChanged: Boolean = false,
    val scheduleChanged: Boolean = false
) : ApplicationEvent(source)
