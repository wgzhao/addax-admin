package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.model.Notification;
import com.wgzhao.addax.admin.repository.NotificationRepo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据中心消息提醒总表 API接口
 *
 * @author 
 */
@Tag(name = "alert")
@RequestMapping("/alert")
@RestController
@AllArgsConstructor
public class AlertController
{
    private final NotificationRepo notificationRepo;

    /**
     * 查询数据中心消息提醒总表数据
     *
     * @return List of {@link Notification}
     */
    @Operation(summary = "查询数据中心消息提醒总表数据", description = "查询数据中心消息提醒总表数据")
    @GetMapping(value = "/list")
    public List<Notification> getList() {
        return notificationRepo.findAll();
    }
}