package com.wgzhao.fsbrowser.service.impl;

import com.wgzhao.fsbrowser.model.Msg;
import com.wgzhao.fsbrowser.repository.MsgRepo;
import com.wgzhao.fsbrowser.service.MsgService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据中心消息提醒总表服务接口实现
 *
 * @author 
 */
@Service
public class MsgServiceImpl implements MsgService {
    
    @Autowired
    private MsgRepo msgRepo;

    @Override
    public List<Msg> findAll() {
            return msgRepo.findAll();
    }

}