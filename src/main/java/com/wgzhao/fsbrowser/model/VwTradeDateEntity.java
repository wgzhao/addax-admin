package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.util.Objects;

@Entity
@Table(name = "VW_TRADE_DATE", schema = "STG01", catalog = "")
public class VwTradeDateEntity {
    @Basic
    @Column(name = "INIT_DATE", nullable = true, precision = 0)
    private Integer initDate;
    @Basic
    @Column(name = "PX", nullable = true, precision = 0)
    private BigInteger px;

    public Integer getInitDate() {
        return initDate;
    }

    public void setInitDate(Integer initDate) {
        this.initDate = initDate;
    }

    public BigInteger getPx() {
        return px;
    }

    public void setPx(BigInteger px) {
        this.px = px;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwTradeDateEntity that = (VwTradeDateEntity) o;
        return Objects.equals(initDate, that.initDate) && Objects.equals(px, that.px);
    }

    @Override
    public int hashCode() {
        return Objects.hash(initDate, px);
    }
}
