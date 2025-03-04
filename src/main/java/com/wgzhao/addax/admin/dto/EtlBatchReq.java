package com.wgzhao.addax.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EtlBatchReq {
    private List<String> tids;
    private String flag;
    private Long retryCnt;
}
