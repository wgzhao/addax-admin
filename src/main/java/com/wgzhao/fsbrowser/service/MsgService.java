package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.Msg;

import java.util.List;

/**
 * 数据中心消息提醒总表 服务接口
 *
 * @author 
 */
public interface MsgService {

      public List<Msg> findAll();
}