package com.wgzhao.fsbrowser.repository.oracle;

import com.wgzhao.fsbrowser.model.oracle.TbDictionary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TbDictionaryRepo extends JpaRepository<TbDictionary, String> {

    List<TbDictionary> findByEntryCode(String entryCode);

    List<TbDictionary> findByEntryCodeOrderByEntryCodeAsc(String entryCode);
}
