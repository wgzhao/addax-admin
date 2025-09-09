package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.TbDictionary;
import com.wgzhao.addax.admin.model.TbDictionaryPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TbDictionaryRepo extends JpaRepository<TbDictionary, TbDictionaryPK> {

    List<TbDictionary> findByEntryCode(String entryCode);

    List<TbDictionary> findByEntryCodeOrderByEntryCodeAsc(String entryCode);

    @Query(value = """
            select entry_value from tb_dictionary where entry_code = 1021 and entry_value < '?:curDate' order by entry_value desc limit 1
            """, nativeQuery = true)
    String getLastBizDate(String curDate);
}
