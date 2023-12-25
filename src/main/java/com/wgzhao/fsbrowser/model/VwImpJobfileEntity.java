package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_JOBFILE", schema = "STG01", catalog = "")
public class VwImpJobfileEntity {
    @Basic
    @Column(name = "JOBKIND", nullable = false, length = 255)
    private String jobkind;
    @Basic
    @Column(name = "JOBFILE", nullable = true, length = 4000)
    private String jobfile;

    public String getJobkind() {
        return jobkind;
    }

    public void setJobkind(String jobkind) {
        this.jobkind = jobkind;
    }

    public String getJobfile() {
        return jobfile;
    }

    public void setJobfile(String jobfile) {
        this.jobfile = jobfile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpJobfileEntity that = (VwImpJobfileEntity) o;
        return Objects.equals(jobkind, that.jobkind) && Objects.equals(jobfile, that.jobfile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobkind, jobfile);
    }
}
