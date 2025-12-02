package com.wgzhao.addax.admin.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
public class SysItemPK
        implements Serializable
{
    private Integer dictCode;
    private String itemKey;

    public SysItemPK()
    {
    }

    public SysItemPK(Integer dictCode, String itemKey)
    {
        this.dictCode = dictCode;
        this.itemKey = itemKey;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SysItemPK that = (SysItemPK) o;
        return dictCode.equals(that.dictCode) && itemKey.equals(that.itemKey);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(dictCode, itemKey);
    }
}
