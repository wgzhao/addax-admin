package com.wgzhao.addax.admin.dto

data class HiveTableColumn(
    val dbId: Int = 0,
    val dbName: String? = null,
    val dbLocation: String? = null,
    val tblId: Int = 0,
    val tblName: String? = null,
    val tblType: String? = null,
    val tblLocation: String? = null,
    val cdId: Int = 0,
    val tblComment: String? = null,
    val colName: String? = null,
    val colType: String? = null,
    val colComment: String? = null,
    val colIdx: Int = 0
)
