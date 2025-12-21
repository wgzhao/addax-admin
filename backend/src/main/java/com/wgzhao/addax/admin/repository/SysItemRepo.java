package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.SysItem;
import com.wgzhao.addax.admin.model.SysItemPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SysItemRepo
    extends JpaRepository<SysItem, SysItemPK>
{

    List<SysItem> findByDictCode(int dictCode);

    List<SysItem> findByDictCodeOrderByDictCodeAsc(int dictCode);

    @Query("SELECT s.itemValue FROM SysItem s WHERE s.dictCode = 1021 AND s.itemValue < :curDate ORDER BY s.itemValue DESC")
    List<String> getLastBizDateList(String curDate);

    @Query("SELECT s FROM SysItem s WHERE s.dictCode = 2011")
    List<SysItem> getHiveTypeItems();

    Optional<SysItem> findByDictCodeAndItemKey(int dictCode, String itemKey);

    @Query(value = """
        SELECT item_value FROM sys_item
        WHERE dict_code = 1021 AND item_key < ?1
        ORDER BY item_key DESC
        LIMIT 1
        """, nativeQuery = true)
    String findLastBizDateList(String curDate);

    List<SysItem> findByDictCodeIn(List<Integer> dictCodes);

    @Query(value = """
        select item_key from sys_item
        where dict_code = 1021 and cast(item_key as timestamp) >= cast(?1 as timestamp) - interval '?2' day
        order by item_key asc limit 1
        """, nativeQuery = true)
    String getBizDateOffset(String bizDate, int days);
}
