package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

/**
 * 数据中心消息提醒总表
 *
 * @author 
 */
public interface NotificationRepo
        extends JpaRepository<Notification, String> {
    List<Notification> findDistinctBydwCltDateAfter(Date td);
}