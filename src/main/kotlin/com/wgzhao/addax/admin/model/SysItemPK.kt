package com.wgzhao.addax.admin.model

import lombok.Getter
import lombok.Setter
import java.io.Serializable
import java.util.*

@Getter
@Setter
class SysItemPK

    : Serializable {
    private var dictCode: Int? = null
    private var itemKey: String? = null

    constructor()

    constructor(dictCode: Int, itemKey: String) {
        this.dictCode = dictCode
        this.itemKey = itemKey
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as SysItemPK
        return dictCode == that.dictCode && itemKey == that.itemKey
    }

    override fun hashCode(): Int {
        return Objects.hash(dictCode, itemKey)
    }
}
