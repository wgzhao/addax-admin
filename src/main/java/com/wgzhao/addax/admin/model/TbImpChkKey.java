package com.wgzhao.addax.admin.model;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

@Embeddable
@Setter
@Getter
public class TbImpChkKey implements Serializable
{
    private String chkContent;
    private Date updtDate;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TbImpChkKey that = (TbImpChkKey) o;
        return Objects.equals(chkContent, that.chkContent) && Objects.equals(updtDate, that.updtDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chkContent, updtDate);
    }
}
