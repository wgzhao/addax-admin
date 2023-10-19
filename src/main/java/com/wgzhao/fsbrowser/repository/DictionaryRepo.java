package com.wgzhao.fsbrowser.repository;

import com.wgzhao.fsbrowser.model.Dictionary;
import org.hibernate.annotations.SQLSelect;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DictionaryRepo extends JpaRepository<Dictionary, String> {

    List<Dictionary> findByEntryCode(String entryCode);
}
