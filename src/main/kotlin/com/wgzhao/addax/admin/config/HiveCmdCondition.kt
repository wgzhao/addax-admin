package com.wgzhao.addax.admin.config

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

class HiveCmdCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        val impl = context.environment.getProperty("target.service.impl")
        return "cmd".equals(impl, ignoreCase = true)
    }
}