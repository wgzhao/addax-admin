package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpChk;
import com.wgzhao.fsbrowser.service.ImpChkService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_CHK API接口
 *
 * @author 
 */
@Api(value = "/impChk", tags = {"TB_IMP_CHKAPI"})
@RequestMapping("/impChk")
@RestController
public class ImpChkController {

    @Autowired
    private ImpChkService impChkService;

    /**
     * 查询TB_IMP_CHK数据
     *
     * @return List of {@link ImpChk}
     */
    @ApiOperation(value = "查询TB_IMP_CHK数据", httpMethod = "GET",tags = {"查询TB_IMP_CHK数据"})
    @GetMapping(value = "/list")
    public List<ImpChk> getList() {
        return impChkService.findAll();
    }
}