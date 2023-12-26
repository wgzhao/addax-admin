package com.wgzhao.fsbrowser.controller;

import com.wgzhao.fsbrowser.model.oracle.Msg;
import com.wgzhao.fsbrowser.service.MsgService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.ApiParam;

import java.util.List;

/**
 * 数据中心消息提醒总表 API接口
 *
 * @author 
 */
@Api(value = "/msg", tags = {"数据中心消息提醒总表API"})
@RequestMapping("/msg")
@RestController
public class MsgController {

    @Autowired
    private MsgService msgService;

    /**
     * 查询数据中心消息提醒总表数据
     *
     * @return List of {@link Msg}
     */
    @ApiOperation(value = "查询数据中心消息提醒总表数据", httpMethod = "GET",tags = {"查询数据中心消息提醒总表数据"})
    @GetMapping(value = "/list")
    public List<Msg> getList() {
        return msgService.findAll();
    }
}