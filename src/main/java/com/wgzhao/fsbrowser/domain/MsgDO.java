package com.wgzhao.fsbrowser.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 数据中心消息提醒总表实体类
 * (该文件自动生成，请勿修改)
 *
 * @author 
 */
public class MsgDO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 自动生成，无需理会
     */
    private String mid;

    /**
     * 接收人号码或者其他唯一标识，用逗号分隔
     */
    private String phone;

    /**
     * 消息内容
     */
    private String msg;

    /**
     * 消息生成的时间，自动生成
     */
    private Date dwCltDate;

    /**
     * 是否发送短信，发送成功后置为y
     */
    private String bsms;

    /**
     * 是否发送KK，发送成功后置为y
     */
    private String bkk;

    /**
     * 是否拨打语音，拨打成功后置为y
     */
    private String bcall;

    public String getMid() {
        return this.mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public String getPhone() {
        return this.phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getMsg() {
        return this.msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Date getDwCltDate() {
        return this.dwCltDate;
    }

    public void setDwCltDate(Date dwCltDate) {
        this.dwCltDate = dwCltDate;
    }

    public String getBsms() {
        return this.bsms;
    }

    public void setBsms(String bsms) {
        this.bsms = bsms;
    }

    public String getBkk() {
        return this.bkk;
    }

    public void setBkk(String bkk) {
        this.bkk = bkk;
    }

    public String getBcall() {
        return this.bcall;
    }

    public void setBcall(String bcall) {
        this.bcall = bcall;
    }

}