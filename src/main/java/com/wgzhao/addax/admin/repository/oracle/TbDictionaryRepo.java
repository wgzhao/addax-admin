package com.wgzhao.addax.admin.repository.oracle;

import com.wgzhao.addax.admin.model.oracle.TbDictionary;
import com.wgzhao.addax.admin.model.oracle.TbDictionaryPK;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TbDictionaryRepo extends JpaRepository<TbDictionary, TbDictionaryPK> {

    List<TbDictionary> findByEntryCode(String entryCode);

    List<TbDictionary> findByEntryCodeOrderByEntryCodeAsc(String entryCode);
}
