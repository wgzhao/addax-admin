package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.model.Notification;
import com.wgzhao.addax.admin.repository.NotificationRepo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据中心消息提醒总表接口，提供消息提醒相关数据查询
 */
@Tag(name = "alert")
@RequestMapping("/alert")
@RestController
@AllArgsConstructor
public class AlertController
{
    /**
     * 消息提醒数据仓库
     */
    private final NotificationRepo notificationRepo;

    /**
     * 查询数据中心消息提醒总表数据
     *
     * @return 消息提醒列表
     */
    @Operation(summary = "查询数据中心消息提醒总表数据", description = "查询数据中心消息提醒总表数据")
    @GetMapping(value = "/list")
    public List<Notification> getList()
    {
        return notificationRepo.findAll();
    }
}
