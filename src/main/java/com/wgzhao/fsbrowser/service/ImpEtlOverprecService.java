package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.ImpEtlOverprec;
import com.wgzhao.fsbrowser.model.oracle.VwImpEtlOverprecEntity;

import java.util.List;
import java.util.Map;

public interface ImpEtlOverprecService {

    List<VwImpEtlOverprecEntity> getAllImpEtlOverprec();

    List<Map<String, Float>> accompListRatio();

}
