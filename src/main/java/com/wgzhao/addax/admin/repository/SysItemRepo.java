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

    @Query("SELECT s.itemValue FROM SysItem s WHERE s.dictCode = 1021 AND s.itemValue < :curDate ORDER BY s.itemValue DESC")
    List<String> getLastBizDateList(String curDate);

    @Query("SELECT s FROM SysItem s WHERE s.dictCode = 2011")
    List<SysItem> getHiveTypeItems();

    SysItem findByDictCodeAndItemKey(int dictCode, String itemKey);

    @Query("SELECT s.itemValue FROM SysItem s WHERE s.dictCode = :dictCode AND s.itemKey < :curDate ORDER BY s.itemKey DESC")
    List<String> findLastBizDateList(int dictCode, String curDate);
}
