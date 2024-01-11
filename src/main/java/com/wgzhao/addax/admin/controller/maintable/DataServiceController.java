package com.wgzhao.addax.admin.controller.maintable;

import com.wgzhao.addax.admin.model.oracle.TbImpDs2Tbls;
import com.wgzhao.addax.admin.model.oracle.VwImpDs2;
import com.wgzhao.addax.admin.repository.oracle.TbImpDs2TblsRepo;
import com.wgzhao.addax.admin.repository.oracle.ViewPseudoRepo;
import com.wgzhao.addax.admin.service.VwImpDs2Service;
import com.wgzhao.addax.admin.utils.CacheUtil;
import com.wgzhao.addax.admin.utils.LogFileUtil;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据服务配置接口
 */
@Api(value="数据服务配置接口", tags = {"主表配置"})
@RestController
@RequestMapping("/maintable/dataService")
public class DataServiceController {

    @Autowired
    private VwImpDs2Service vwImpDs2Service;

    @Autowired
    private TbImpDs2TblsRepo tbImpDs2TblsRepo;

    @Autowired
    private ViewPseudoRepo viewPseudoRepo;

    @GetMapping({"/list", "/"})
    public List<VwImpDs2> list()
    {
        return vwImpDs2Service.getAllDs();
    }

    @GetMapping("/detail/{id}")
    public Optional<VwImpDs2> detail(@PathVariable("id") String id)
    {
        return vwImpDs2Service.getDsInfo(id);
    }

    // 获得数据推送表详情
    @GetMapping("/dsTable/{id}")
    public List<TbImpDs2Tbls> getDsTable(@PathVariable("id") String id)
    {
        return tbImpDs2TblsRepo.findByDsId(id);
    }

    // 获得推送表字段详情
    @GetMapping("/dsTableFields/{tbl}")
    public List<Map<String, Object>> getDsTableFields(@PathVariable("tbl") String tbl)
    {
        return viewPseudoRepo.findTableFields(tbl);
    }

}
