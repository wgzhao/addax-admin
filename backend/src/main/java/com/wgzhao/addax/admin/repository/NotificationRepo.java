package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 数据中心消息提醒总表
 *
 * @author wgzhao@gmail.com
 */
public interface NotificationRepo
    extends JpaRepository<Notification, String>
{

}
