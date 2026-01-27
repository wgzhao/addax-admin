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
        where dict_code = 1021 and item_key  >= to_char(
            (to_timestamp(?1, 'YYYYMMDD') + (?2 || ' day')::interval),
            'YYYYMMDD'
          )
        order by item_key limit 1
        """, nativeQuery = true)
    String getBizDateOffset(String bizDate, int days);
}
