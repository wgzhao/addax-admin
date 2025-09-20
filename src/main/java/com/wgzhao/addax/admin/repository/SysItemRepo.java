package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.model.SysItemPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SysItemRepo
        extends JpaRepository<SysItem, SysItemPK>
{

    List<SysItem> findByDictCode(int dictCode);

    List<SysItem> findByDictCodeOrderByDictCodeAsc(int dictCode);

    @Query(value = """
            select item_value from sys_item where dict_code = 1021 and item_value < ?1 order by item_value desc limit 1
            """, nativeQuery = true)
    String getLastBizDate(String curDate);

    @Query(value = """
            select item_name, item_value from sys_item where dict_code = 2011
            """, nativeQuery = true)
    Map<String, String> getHiveTypeMap();

    SysItem findByDictCodeAndItemKey(int dictCode, String itemKey);

    @Query(value = """
            select entry_value from sys_item where dict_code = ?1 and item_key < ?2 order by item_key desc limit 1
            """, nativeQuery = true)
    String findLastBizDate(int dictCode, String curDate);
}
