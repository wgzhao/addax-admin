package com.wgzhao.addax.admin.service;

import com.wgzhao.addax.admin.repository.TbImpSpNeedtabRepo;
import com.wgzhao.addax.admin.model.TbImpSpNeedtab;
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
