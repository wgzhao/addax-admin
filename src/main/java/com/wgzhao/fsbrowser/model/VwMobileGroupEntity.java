package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_MOBILE_GROUP", schema = "STG01", catalog = "")
public class VwMobileGroupEntity {
    @Basic
    @Column(name = "GROUPID", nullable = true, length = 4000)
    private String groupid;
    @Basic
    @Column(name = "MOBILE", nullable = false, length = 255)
    private String mobile;
    @Basic
    @Column(name = "USERNAME", nullable = true, length = 4000)
    private String username;

    public String getGroupid() {
        return groupid;
    }

    public void setGroupid(String groupid) {
        this.groupid = groupid;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwMobileGroupEntity that = (VwMobileGroupEntity) o;
        return Objects.equals(groupid, that.groupid) && Objects.equals(mobile, that.mobile) && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupid, mobile, username);
    }
}
