package com.wgzhao.addax.admin.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;

@Entity
@Table(name = "tb_imp_plan")
@Setter
@Getter
public class TbImpPlan {
    @Basic
    @Column(name = "pn_type")
    private String pnType;
    @Basic
    @Column(name = "pn_fixed")
    private String pnFixed;
    @Basic
    @Column(name = "pn_interval")
    private Short pnInterval;
    @Basic
    @Column(name = "pn_range")
    private String pnRange;
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "pn_id")
    private String pnId;
    @Basic
    @Column(name = "flag")
    private String flag;
    @Basic
    @Column(name = "start_time")
    private Date startTime;
    @Basic
    @Column(name = "end_time")
    private Date endTime;
    @Basic
    @Column(name = "bexit")
    private String bexit;
    @Basic
    @Column(name = "runtime")
    private Integer runtime;


    public enum PnType {
        DAILY("每天"),
        WEEKLY("W"),
        CURR_TRADE("交易日当天"),
        TRADE_DATE("交易日或标志");
        private final String value;

        PnType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
