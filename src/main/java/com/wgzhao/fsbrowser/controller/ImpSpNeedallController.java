package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpSpNeedall;
import com.wgzhao.fsbrowser.service.ImpSpNeedallService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_SP_NEEDALL API接口
 *
 * @author 
 */
@Api(value = "/impSpNeedall", tags = {"TB_IMP_SP_NEEDALLAPI"})
@RequestMapping("/impSpNeedall")
@RestController
public class ImpSpNeedallController {

    @Autowired
    private ImpSpNeedallService impSpNeedallService;

    /**
     * 查询TB_IMP_SP_NEEDALL数据
     *
     * @return List of {@link ImpSpNeedall}
     */
    @ApiOperation(value = "查询TB_IMP_SP_NEEDALL数据", httpMethod = "GET",tags = {"查询TB_IMP_SP_NEEDALL数据"})
    @GetMapping(value = "/list")
    public List<ImpSpNeedall> getList() {
        return impSpNeedallService.findAll();
    }
}