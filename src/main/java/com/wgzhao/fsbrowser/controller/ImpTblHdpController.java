package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpTblHdp;
import com.wgzhao.fsbrowser.service.ImpTblHdpService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_TBL_HDP API接口
 *
 * @author 
 */
@Api(value = "/impTblHdp", tags = {"TB_IMP_TBL_HDPAPI"})
@RequestMapping("/impTblHdp")
@RestController
public class ImpTblHdpController {

    @Autowired
    private ImpTblHdpService impTblHdpService;

    /**
     * 查询TB_IMP_TBL_HDP数据
     *
     * @return List of {@link ImpTblHdp}
     */
    @ApiOperation(value = "查询TB_IMP_TBL_HDP数据", httpMethod = "GET",tags = {"查询TB_IMP_TBL_HDP数据"})
    @GetMapping(value = "/list")
    public List<ImpTblHdp> getList() {
        return impTblHdpService.findAll();
    }
}