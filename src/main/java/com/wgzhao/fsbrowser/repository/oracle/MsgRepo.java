package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.Msg;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 数据中心消息提醒总表
 *
 * @author 
 */
public interface MsgRepo extends JpaRepository<Msg, String> {

}