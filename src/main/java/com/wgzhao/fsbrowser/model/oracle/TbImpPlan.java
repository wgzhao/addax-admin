package com.wgzhao.fsbrowser.model.oracle;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;
import java.util.Objects;

@Entity
@Table(name = "TB_IMP_PLAN", schema = "STG01", catalog = "")
@Setter
@Getter
public class TbImpPlan {
    @Basic
    @Column(name = "PN_TYPE")
    private String pnType;
    @Basic
    @Column(name = "PN_FIXED")
    private String pnFixed;
    @Basic
    @Column(name = "PN_INTERVAL")
    private Short pnInterval;
    @Basic
    @Column(name = "PN_RANGE")
    private String pnRange;
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "PN_ID")
    private String pnId;
    @Basic
    @Column(name = "FLAG")
    private String flag;
    @Basic
    @Column(name = "START_TIME")
    private Date startTime;
    @Basic
    @Column(name = "END_TIME")
    private Date endTime;
    @Basic
    @Column(name = "BEXIT")
    private String bexit;
    @Basic
    @Column(name = "RUNTIME")
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
