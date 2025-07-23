package com.wgzhao.addax.admin.controller.maintable;

import com.wgzhao.addax.admin.dto.ApiResponse;
import com.wgzhao.addax.admin.model.TbImpDs2Tbls;
import com.wgzhao.addax.admin.model.VwImpDs2;
import com.wgzhao.addax.admin.repository.TbImpDs2TblsRepo;
import com.wgzhao.addax.admin.repository.ViewPseudoRepo;
import com.wgzhao.addax.admin.service.VwImpDs2Service;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ApiResponse<List<VwImpDs2>> list() {
        return ApiResponse.success(vwImpDs2Service.getAllDs());
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<Optional<VwImpDs2>> detail(@PathVariable("id") String id) {
        return ApiResponse.success(vwImpDs2Service.getDsInfo(id));
    }

    // 获得数据推送表详情
    @GetMapping("/dsTable/{id}")
    public ApiResponse<List<TbImpDs2Tbls>> getDsTable(@PathVariable("id") String id) {
        return ApiResponse.success(tbImpDs2TblsRepo.findByDsId(id));
    }

    // 获得推送表字段详情
    @GetMapping("/dsTableFields/{tbl}")
    public ApiResponse<List<Map<String, Object>>> getDsTableFields(@PathVariable("tbl") String tbl) {
        return ApiResponse.success(viewPseudoRepo.findTableFields(tbl));
    }
}
