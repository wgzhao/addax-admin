package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.TbImpSpNeedtab;
import com.wgzhao.fsbrowser.repository.oracle.TbImpSpNeedtabRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TbImpSpNeedtabService {

    @Autowired
    private TbImpSpNeedtabRepo tbImpSpNeedtabRepo;
    public List<TbImpSpNeedtab> getNeedtablesByTablename(String tablename, String sysId) {
        return tbImpSpNeedtabRepo.findByTableName(tablename.toLowerCase(), sysId.toLowerCase());
    }
}
