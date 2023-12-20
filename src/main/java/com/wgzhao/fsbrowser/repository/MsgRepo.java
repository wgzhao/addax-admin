package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.Msg;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 数据中心消息提醒总表
 *
 * @author 
 */
public interface MsgRepo extends JpaRepository<Msg, String> {

}