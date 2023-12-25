package com.wgzhao.fsbrowser.model;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "VW_IMP_SP_DEST_EQUAL", schema = "STG01", catalog = "")
public class VwImpSpDestEqualEntity {
    @Basic
    @Column(name = "SP_ID", nullable = true, length = 32)
    private String spId;
    @Basic
    @Column(name = "DEST", nullable = true, length = 511)
    private String dest;

    public String getSpId() {
        return spId;
    }

    public void setSpId(String spId) {
        this.spId = spId;
    }

    public String getDest() {
        return dest;
    }

    public void setDest(String dest) {
        this.dest = dest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VwImpSpDestEqualEntity that = (VwImpSpDestEqualEntity) o;
        return Objects.equals(spId, that.spId) && Objects.equals(dest, that.dest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(spId, dest);
    }
}
