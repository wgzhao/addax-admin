package com.wgzhao.fsbrowser.model.pg;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(name = "tb_addax_sta", schema = "public", catalog = "stg01")
@Data
@Setter
@Getter
public class TbAddaxStaEntity {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "pkid")
    private String pkid;
    @Basic
    @Column(name = "jobname")
    private String jobname;
    @Basic
    @Column(name = "start_ts")
    private Integer startTs;
    @Basic
    @Column(name = "end_ts")
    private Integer endTs;
    @Basic
    @Column(name = "take_secs")
    private Integer takeSecs;
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
    @Basic
    @Column(name = "updt_date")
    private Timestamp updtDate;

}
