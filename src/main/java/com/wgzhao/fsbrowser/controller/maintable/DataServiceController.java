package com.wgzhao.fsbrowser.controller.maintable;

import com.wgzhao.fsbrowser.model.oracle.TbImpDs2Tbls;
import com.wgzhao.fsbrowser.model.oracle.VwImpDs2;
import com.wgzhao.fsbrowser.repository.oracle.TbImpDs2TblsRepo;
import com.wgzhao.fsbrowser.service.VwImpDs2Service;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
}
