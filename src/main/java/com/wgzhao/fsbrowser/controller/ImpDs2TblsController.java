package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpDs2Tbls;
import com.wgzhao.fsbrowser.service.ImpDs2TblsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_DS2_TBLS API接口
 *
 * @author 
 */
@Api(value = "/impDs2Tbls", tags = {"TB_IMP_DS2_TBLSAPI"})
@RequestMapping("/impDs2Tbls")
@RestController
public class ImpDs2TblsController {

    @Autowired
    private ImpDs2TblsService impDs2TblsService;

    /**
     * 查询TB_IMP_DS2_TBLS数据
     *
     * @return List of {@link ImpDs2Tbls}
     */
    @ApiOperation(value = "查询TB_IMP_DS2_TBLS数据", httpMethod = "GET",tags = {"查询TB_IMP_DS2_TBLS数据"})
    @GetMapping(value = "/list")
    public List<ImpDs2Tbls> getList() {
        return impDs2TblsService.findAll();
    }
}