package com.wgzhao.addax.admin.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class HiveJdbcCondition implements Condition
{
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata)
    {
        String impl = context.getEnvironment().getProperty("target.service.impl");
        return "jdbc".equalsIgnoreCase(impl);
    }
}

