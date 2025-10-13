package com.wgzhao.addax.admin.event

import lombok.Getter
import lombok.Setter
import org.springframework.context.ApplicationEvent

/**
 * 数据源更新事件，当数据源信息变更时发布此事件。
 * 监听此事件的组件可以根据需要进行相应的处理，例如更新相关作业配置等。
 */
@Getter
@Setter
class SourceUpdatedEvent @JvmOverloads constructor(
    source: Any,
    private val sourceId: Int,
    private val connectionChanged: Boolean = false,
    private val scheduleChanged: Boolean = false
) : ApplicationEvent(source)
