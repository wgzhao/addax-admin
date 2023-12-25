package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.ImpSpNeedtab;
import com.wgzhao.fsbrowser.service.ImpSpNeedtabService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;
import java.util.Map;

/**
 * TB_IMP_SP_NEEDTAB API接口
 *
 * @author 
 */
@Api(value = "/impSpNeedtab", tags = {"TB_IMP_SP_NEEDTABAPI"})
@RequestMapping("/impSpNeedtab")
@RestController
@CrossOrigin
public class ImpSpNeedtabController {

    @Autowired
    private ImpSpNeedtabService impSpNeedtabService;

    /**
     * 查询TB_IMP_SP_NEEDTAB数据
     *
     * @return List of {@link ImpSpNeedtab}
     */
    @ApiOperation(value = "查询TB_IMP_SP_NEEDTAB数据", httpMethod = "GET",tags = {"查询TB_IMP_SP_NEEDTAB数据"})
    @GetMapping(value = "/list")
    public List<ImpSpNeedtab> getList() {
        return impSpNeedtabService.findAll();
    }

    /**
     * SP 计算 -- 使用场景 u
     */

    @GetMapping(value="/scene")
    public List<Map> getScenes(@RequestParam(value="tbl") String tableName) {
        return impSpNeedtabService.findSceneByTableName(tableName);
    }
}