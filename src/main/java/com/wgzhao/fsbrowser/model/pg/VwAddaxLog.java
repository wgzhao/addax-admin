package com.wgzhao.fsbrowser.model.pg;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Entity
@Table(name = "vw_addax_log", schema = "public", catalog = "stg01")
@Setter
@Getter
public class VwAddaxLog {

    @Id
    @Basic
    @Column(name = "start_day")
    private String startDay;
    
    @Basic
    @Column(name = "spname")
    private String spname;
    @Basic
    @Column(name = "start_time")
    private String startTime;
    @Basic
    @Column(name = "end_time")
    private String endTime;
    @Basic
    @Column(name = "runtime")
    private Integer runtime;
    @Basic
    @Column(name = "byte_speed")
    private Integer byteSpeed;
    @Basic
    @Column(name = "rec_speed")
    private Integer recSpeed;
    @Basic
    @Column(name = "total_rec")
    private Integer totalRec;
    @Basic
    @Column(name = "total_err")
    private Integer totalErr;
}
