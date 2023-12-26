package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.ImpFlag;
import com.wgzhao.fsbrowser.service.ImpFlagService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_FLAG API接口
 *
 * @author 
 */
@Api(value = "/impFlag", tags = {"TB_IMP_FLAGAPI"})
@RequestMapping("/impFlag")
@RestController
public class ImpFlagController {

    @Autowired
    private ImpFlagService impFlagService;

    /**
     * 查询TB_IMP_FLAG数据
     *
     * @return List of {@link ImpFlag}
     */
    @ApiOperation(value = "查询TB_IMP_FLAG数据", httpMethod = "GET",tags = {"查询TB_IMP_FLAG数据"})
    @GetMapping(value = "/list")
    public List<ImpFlag> getList() {
        return impFlagService.findAll();
    }
}