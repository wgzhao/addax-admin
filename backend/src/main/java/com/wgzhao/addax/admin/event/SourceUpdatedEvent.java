package com.wgzhao.addax.admin.event;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * 数据源更新事件，当数据源信息变更时发布此事件。
 * 监听此事件的组件可以根据需要进行相应的处理，例如更新相关作业配置等。
 */
@Getter
@Setter
public class SourceUpdatedEvent
    extends ApplicationEvent
{
    private final int sourceId;
    private final boolean connectionChanged;

    public SourceUpdatedEvent(Object source, int sourceId, boolean connectionChanged)
    {
        super(source);
        this.sourceId = sourceId;
        this.connectionChanged = connectionChanged;
    }
}
