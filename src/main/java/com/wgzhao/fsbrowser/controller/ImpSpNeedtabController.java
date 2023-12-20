package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpSpNeedtab;
import com.wgzhao.fsbrowser.service.ImpSpNeedtabService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_SP_NEEDTAB API接口
 *
 * @author 
 */
@Api(value = "/impSpNeedtab", tags = {"TB_IMP_SP_NEEDTABAPI"})
@RequestMapping("/impSpNeedtab")
@RestController
public class ImpSpNeedtabController {

    @Autowired
    private ImpSpNeedtabService impSpNeedtabService;

    /**
     * 查询TB_IMP_SP_NEEDTAB数据
     *
     * @return List of {@link ImpSpNeedtab}
     */
    @ApiOperation(value = "查询TB_IMP_SP_NEEDTAB数据", httpMethod = "GET",tags = {"查询TB_IMP_SP_NEEDTAB数据"})
    @GetMapping(value = "/list")
    public List<ImpSpNeedtab> getList() {
        return impSpNeedtabService.findAll();
    }
}