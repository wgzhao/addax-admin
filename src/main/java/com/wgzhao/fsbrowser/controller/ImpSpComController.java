package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpSpCom;
import com.wgzhao.fsbrowser.service.ImpSpComService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * HADOOP_SP的运行脚本（作为主表的附属表） API接口
 *
 * @author 
 */
@Api(value = "/impSpCom", tags = {"HADOOP_SP的运行脚本（作为主表的附属表）API"})
@RequestMapping("/impSpCom")
@RestController
public class ImpSpComController {

    @Autowired
    private ImpSpComService impSpComService;

    /**
     * 查询HADOOP_SP的运行脚本（作为主表的附属表）数据
     *
     * @return List of {@link ImpSpCom}
     */
    @ApiOperation(value = "查询HADOOP_SP的运行脚本（作为主表的附属表）数据", httpMethod = "GET",tags = {"查询HADOOP_SP的运行脚本（作为主表的附属表）数据"})
    @GetMapping(value = "/list")
    public List<ImpSpCom> getList() {
        return impSpComService.findAll();
    }
}