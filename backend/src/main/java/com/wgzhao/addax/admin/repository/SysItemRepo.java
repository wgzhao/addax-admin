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

    /**
     * The largest date in a calendar dictionary that is strictly before {@code curDate}.
     *
     * <p>A calendar dictionary stores one row per business date and nothing else, so this single
     * primitive answers both "is this date a business day" and "what is the previous business day".
     * Values are zero padded {@code yyyyMMdd} strings, which is why comparing them as strings is a
     * valid date comparison.</p>
     */
    @Query(value = """
        SELECT item_value FROM sys_item
        WHERE dict_code = ?1 AND item_key < ?2
        ORDER BY item_key DESC
        LIMIT 1
        """, nativeQuery = true)
    String findLastCalendarDateBefore(int dictCode, String curDate);

    /**
     * Last date a calendar dictionary covers; a value before today means the calendar ran out of data.
     */
    @Query(value = "SELECT max(item_key) FROM sys_item WHERE dict_code = ?1", nativeQuery = true)
    String findMaxCalendarDate(int dictCode);

    @Query(value = """
        SELECT item_key FROM sys_item
        WHERE dict_code = ?1 AND item_key >= ?2 AND item_key <= ?3
        ORDER BY item_key
        """, nativeQuery = true)
    List<String> findCalendarDatesBetween(int dictCode, String from, String to);

    List<SysItem> findByDictCodeIn(List<Integer> dictCodes);

    @Query(value = """
        select item_key from sys_item
        where dict_code = ?3 and item_key  >= to_char(
            (to_timestamp(?1, 'YYYYMMDD') + (?2 || ' day')::interval),
            'YYYYMMDD'
          )
        order by item_key limit 1
        """, nativeQuery = true)
    String getCalendarDateOffset(String bizDate, int days, int dictCode);
}
