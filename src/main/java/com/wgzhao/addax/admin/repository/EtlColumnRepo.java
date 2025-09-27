package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.EtlColumn;
import com.wgzhao.addax.admin.model.EtlColumnPk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface EtlColumnRepo
        extends JpaRepository<EtlColumn, EtlColumnPk>
{
    List<EtlColumn> findAllByTidOrderByColumnId(long tid);
    
//    @Modifying
//    @Transactional
//    int updateColumnNameByTidAndColumnId(Long tid, int columnId, String placeholder);
}
