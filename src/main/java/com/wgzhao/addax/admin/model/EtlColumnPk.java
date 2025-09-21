package com.wgzhao.addax.admin.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
public class EtlColumnPk
        implements Serializable
{
    private long tid;
    private String columnName;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EtlColumnPk that = (EtlColumnPk) o;
        return tid == that.tid && columnName.equals(that.columnName);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(tid) * 31 + columnName.hashCode();
    }
}
