package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.Dictionary;

import java.util.List;

public interface DictionaryService {

    List<Dictionary> findByEntryCode(String entryCode);
}
