package com.wgzhao.addax.admin.repository

import com.wgzhao.addax.admin.model.SysItem
import com.wgzhao.addax.admin.model.SysItemPK
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface SysItemRepo : JpaRepository<SysItem, SysItemPK> {

    fun findByDictCodeOrderByDictCodeAsc(dictCode: Int): MutableList<SysItem?>?

    @Query("SELECT s.itemValue FROM SysItem s WHERE s.dictCode = 1021 AND s.itemValue < :curDate ORDER BY s.itemValue DESC")
    fun getLastBizDateList(curDate: String?): MutableList<String?>?

    @get:Query("SELECT s FROM SysItem s WHERE s.dictCode = 2011")
    val hiveTypeItems: MutableList<SysItem?>?

    fun findByDictCodeAndItemKey(dictCode: Int, itemKey: String?): Optional<SysItem?>?

    @Query(
        value = """
            SELECT item_value FROM sys_item
            WHERE dict_code = 1021 AND item_key < ?1
            ORDER BY item_key DESC
            LIMIT 1
            
            """, nativeQuery = true
    )
    fun findLastBizDateList(curDate: String?): String?
}
