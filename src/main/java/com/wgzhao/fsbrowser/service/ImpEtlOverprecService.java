package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.ImpEtlOverprec;

import java.util.List;
import java.util.Map;

public interface ImpEtlOverprecService {

    List<ImpEtlOverprec> getAllImpEtlOverprec();

    List<Map<String, Float>> accompListRatio();

}
