package com.wgzhao.addax.admin.controller;

import com.wgzhao.addax.admin.model.Msg;
import com.wgzhao.addax.admin.repository.MsgRepo;
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
@Api(value = "/alert")
@RequestMapping("/alert")
@RestController
public class AlertController
{

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