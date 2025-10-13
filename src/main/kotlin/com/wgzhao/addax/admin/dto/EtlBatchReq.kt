package com.wgzhao.addax.admin.dto

import lombok.AllArgsConstructor
import lombok.Data
import lombok.NoArgsConstructor

@Data
@AllArgsConstructor
@NoArgsConstructor
class EtlBatchReq {
    private var tids: MutableList<Long?>? = null
    private var status: String? = null
    private var retryCnt = 0
}
