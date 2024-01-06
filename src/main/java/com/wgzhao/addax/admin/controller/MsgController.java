package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.model.oracle.Msg;
import com.wgzhao.addax.admin.repository.oracle.MsgRepo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private MsgRepo msgRepo;

    /**
     * 查询数据中心消息提醒总表数据
     *
     * @return List of {@link Msg}
     */
    @ApiOperation(value = "查询数据中心消息提醒总表数据", httpMethod = "GET",tags = {"查询数据中心消息提醒总表数据"})
    @GetMapping(value = "/list")
    public List<Msg> getList() {
        return msgRepo.findAll();
    }
}