package com.wgzhao.addax.admin.repository;

import com.wgzhao.addax.admin.model.TbDictionary;
import com.wgzhao.addax.admin.model.TbDictionaryPK;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TbDictionaryRepo extends JpaRepository<TbDictionary, TbDictionaryPK> {

    List<TbDictionary> findByEntryCode(String entryCode);

    List<TbDictionary> findByEntryCodeOrderByEntryCodeAsc(String entryCode);
}
