package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.TmpImpSpNeedtab;
import com.wgzhao.fsbrowser.service.TmpImpSpNeedtabService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TMP_IMP_SP_NEEDTAB API接口
 *
 * @author 
 */
@Api(value = "/tmpImpSpNeedtab", tags = {"TMP_IMP_SP_NEEDTABAPI"})
@RequestMapping("/tmpImpSpNeedtab")
@RestController
public class TmpImpSpNeedtabController {

    @Autowired
    private TmpImpSpNeedtabService tmpImpSpNeedtabService;

    /**
     * 查询TMP_IMP_SP_NEEDTAB数据
     *
     * @return List of {@link TmpImpSpNeedtab}
     */
    @ApiOperation(value = "查询TMP_IMP_SP_NEEDTAB数据", httpMethod = "GET",tags = {"查询TMP_IMP_SP_NEEDTAB数据"})
    @GetMapping(value = "/list")
    public List<TmpImpSpNeedtab> getList() {
        return tmpImpSpNeedtabService.findAll();
    }
}