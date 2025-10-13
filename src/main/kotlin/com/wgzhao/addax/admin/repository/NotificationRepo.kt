package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.Notification
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

/**
 * 数据中心消息提醒总表
 *
 * @author
 */
interface NotificationRepo

    : JpaRepository<Notification?, String?> {
    fun findDistinctByCreateAtAfter(td: Date?): MutableList<Notification?>?

    fun findAllByImOrderByCreateAtAsc(y: String?): MutableList<Notification?>?
}