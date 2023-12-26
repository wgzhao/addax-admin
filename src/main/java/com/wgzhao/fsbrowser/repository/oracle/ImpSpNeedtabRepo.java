package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.ImpSpNeedtab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * TB_IMP_SP_NEEDTAB
 *
 * @author 
 */
public interface ImpSpNeedtabRepo extends JpaRepository<ImpSpNeedtab, String> {


    @Query(value="select distinct decode(kind,'ALL','SP基础表','DS','数据服务源表','NEEDS','SP前置依赖','其他类型') as kind,\n" +
            "to_char(fn_imp_value('taskname',sp_id)) used\n" +
            "from tb_imp_sp_needtab\n" +
            "where lower(table_name)=lower(?1) \n" +
            "  and kind in('ALL','DS','NEEDS')\n" +
            "order by 1,2", nativeQuery = true)
    List<Map> findSceneByTableName(String tableName);

}