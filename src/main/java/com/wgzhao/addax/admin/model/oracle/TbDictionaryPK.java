package com.wgzhao.addax.admin.model.oracle;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
public class TbDictionaryPK implements Serializable {
    private String entryCode;
    private String entryValue;

    public TbDictionaryPK() {
    }

    public TbDictionaryPK(String entryCode, String entryValue) {
        this.entryCode = entryCode;
        this.entryValue = entryValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TbDictionaryPK that = (TbDictionaryPK) o;
        return entryCode.equals(that.entryCode) && entryValue.equals(that.entryValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryCode, entryValue);
    }
}
