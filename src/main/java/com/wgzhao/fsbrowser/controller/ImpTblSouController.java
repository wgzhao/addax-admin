package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpTblSou;
import com.wgzhao.fsbrowser.service.ImpTblSouService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_TBL_SOU API接口
 *
 * @author 
 */
@Api(value = "/impTblSou", tags = {"TB_IMP_TBL_SOUAPI"})
@RequestMapping("/impTblSou")
@RestController
public class ImpTblSouController {

    @Autowired
    private ImpTblSouService impTblSouService;

    /**
     * 查询TB_IMP_TBL_SOU数据
     *
     * @return List of {@link ImpTblSou}
     */
    @ApiOperation(value = "查询TB_IMP_TBL_SOU数据", httpMethod = "GET",tags = {"查询TB_IMP_TBL_SOU数据"})
    @GetMapping(value = "/list")
    public List<ImpTblSou> getList() {
        return impTblSouService.findAll();
    }
}