package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.TbImpSp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;

/**
 * HADOOP_SP的配置主表
 *
 * @author 
 */
public interface TbImpSpRepo extends JpaRepository<TbImpSp, String> {
    @Query(value = "select t.needs, \n" +
            "to_char(fn_imp_value('taskname', t.needs)) as needs_name,\n" +
            "t.needs_flag,\n" +
            "to_char(t.needs_end_time, 'YYYY-MM-dd HH:mm:ss') as needs_end_time \n" +
            "from vw_imp_sp_needs t\n" +
            "where t.sp_id = ?1 \n" +
            "order by t.needs_end_time", nativeQuery = true)
    List<Map<String, String>> findRequires(String spId);

//    @Query(value = "select parent_id || decode(level,1,'',level - 1) || decode(level,1,'',connect_by_root(cate_id)) as parent, \n" +
//            "cate_id || level || connect_by_root(cate_id) as cate_id, name, cate_value \n" +
//            "from vw_imp_sp_source start with parent_id= ?1 \n" +
//            "connect by prior cate_id = parent_id\n" +
//            "union all \n" +
//            "select * from vw_imp_sp_source \n" +
//            "where cate_id= ?1 and parent_id is NULL", nativeQuery = true)
    //List<Map<String, Object>> findLineage(String spId);

    @Query(value = "SELECT " +
            "    a.need_sou, " +
            "    a.need_sp, " +
            "    a.sp_alltabs, " +
            "    a.sp_dest, " +
            "    to_char(a.through_need_sou) as through_need_sou, " +
            "    to_char(a.through_need_sp) as through_need_sp, " +
            "    to_char(fn_imp_value('sp_allnext', a.sp_id)) AS sp_allnext " +
            "FROM vw_imp_sp a " +
            "WHERE " +
            "    a.sp_id = ?1 ", nativeQuery = true)
    Map<String, String> findAllNeeds(String spId);
}