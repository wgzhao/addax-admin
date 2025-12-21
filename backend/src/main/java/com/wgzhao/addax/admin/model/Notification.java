package com.wgzhao.addax.admin.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 数据中心消息提醒总表 实体类
 *
 * @author
 */
@Entity
@Table(name = "notification")
@Setter
@Getter
@Data
public class Notification
{

    // 自动生成，无需理会
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    // 接收人号码或者其他唯一标识，用逗号分隔

    @Column(name = "phone")
    private String phone;

    // 消息内容

    @Column(name = "msg")
    private String msg;

    // 是否发送短信，发送成功后置为y

    @Column(name = "sms")
    private String sms;

    // 是否发送KK，发送成功后置为y

    @Column(name = "im")
    private String im;

    // 是否拨打语音，拨打成功后置为y

    @Column(name = "call")
    private String call;

    @Column(name = "create_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createAt;
}
