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
    private int columnId;

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EtlColumnPk that = (EtlColumnPk) o;
        return tid == that.tid && columnId == that.columnId;
    }

    @Override
    public int hashCode()
    {
        return Long.hashCode(tid) * 31 + columnId;
    }
}
