package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.ImpChkInf;
import com.wgzhao.fsbrowser.service.ImpChkInfService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_CHK_INF API接口
 *
 * @author 
 */
@Api(value = "/impChkInf", tags = {"TB_IMP_CHK_INFAPI"})
@RequestMapping("/impChkInf")
@RestController
public class ImpChkInfController {

    @Autowired
    private ImpChkInfService impChkInfService;

    /**
     * 查询TB_IMP_CHK_INF数据
     *
     * @return List of {@link ImpChkInf}
     */
    @ApiOperation(value = "查询TB_IMP_CHK_INF数据", httpMethod = "GET",tags = {"查询TB_IMP_CHK_INF数据"})
    @GetMapping(value = "/list")
    public List<ImpChkInf> getList() {
        return impChkInfService.findAll();
    }
}