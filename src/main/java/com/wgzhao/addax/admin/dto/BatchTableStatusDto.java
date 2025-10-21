package com.wgzhao.addax.admin.dto;

import java.util.List;

public record BatchTableStatusDto(List<Long> tids, String status, int retryCnt)
{
}

