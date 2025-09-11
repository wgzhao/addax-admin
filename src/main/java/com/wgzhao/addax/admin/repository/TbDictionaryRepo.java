package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.TbDictionary;
import com.wgzhao.addax.admin.model.TbDictionaryPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;

public interface TbDictionaryRepo extends JpaRepository<TbDictionary, TbDictionaryPK> {

    List<TbDictionary> findByEntryCode(int entryCode);

    List<TbDictionary> findByEntryCodeOrderByEntryCodeAsc(int entryCode);

    @Query(value = """
            select entry_value from tb_dictionary where entry_code = 1021 and entry_value < ?1 order by entry_value desc limit 1
            """, nativeQuery = true)
    String getLastBizDate(String curDate);

    @Query(value = """
                select entry_value, entry_content from tb_dictionary where entry_code = 2011
                """, nativeQuery = true)
    Map<String, String> getHiveTypeMap();
}
