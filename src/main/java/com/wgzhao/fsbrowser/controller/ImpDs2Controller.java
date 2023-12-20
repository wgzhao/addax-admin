package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpDs2;
import com.wgzhao.fsbrowser.service.ImpDs2Service;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * TB_IMP_DS2 API接口
 *
 * @author 
 */
@Api(value = "/impDs2", tags = {"TB_IMP_DS2API"})
@RequestMapping("/impDs2")
@RestController
public class ImpDs2Controller {

    @Autowired
    private ImpDs2Service impDs2Service;

    /**
     * 查询TB_IMP_DS2数据
     *
     * @return List of {@link ImpDs2}
     */
    @ApiOperation(value = "查询TB_IMP_DS2数据", httpMethod = "GET",tags = {"查询TB_IMP_DS2数据"})
    @GetMapping(value = "/list")
    public List<ImpDs2> getList() {
        return impDs2Service.findAll();
    }
}