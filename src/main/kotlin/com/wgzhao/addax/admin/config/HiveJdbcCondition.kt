package com.wgzhao.addax.admin.config

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class HiveJdbcCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val impl = context.getEnvironment().getProperty("target.service.impl")
        return "jdbc".equals(impl, ignoreCase = true)
    }
}

