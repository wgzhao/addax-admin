package com.wgzhao.fsbrowser.model.oracle;

import java.io.Serializable;

public class TbDictionaryPK implements Serializable {
    private String entryCode;
    private String entryValue;

    public TbDictionaryPK() {
    }
    public TbDictionaryPK(String entryCode, String entryValue) {
        this.entryCode = entryCode;
        this.entryValue = entryValue;
    }
}
