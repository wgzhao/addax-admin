package com.wgzhao.addax.admin.controller

import com.wgzhao.addax.admin.model.Notification
import com.wgzhao.addax.admin.repository.NotificationRepo
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * 数据中心消息提醒总表接口，提供消息提醒相关数据查询
 */
@Tag(name = "alert")
@RestController
@RequestMapping("/alert")
class AlertController(
    private val notificationRepo: NotificationRepo
) {
    private val log = KotlinLogging.logger {}

    @GetMapping("/list")
    @Operation(summary = "查询数据中心消息提醒总表数据", description = "查询数据中心消息提醒总表数据")
    fun list(): List<Notification> = notificationRepo.findAll().filterNotNull()
}