package com.wgzhao.addax.admin.utils;

import com.wgzhao.addax.admin.dto.SortBy;
import org.springframework.data.domain.Sort;

public class QueryUtil
{
    public static Sort generateSort(SortBy sortBy) {
        Sort sort = Sort.unsorted();
        if (sortBy != null && sortBy.getKey() != null && !sortBy.getKey().isEmpty()) {
            String sortOrder = sortBy.getOrder();
            String sortField = sortBy.getKey();
            if ("asc".equalsIgnoreCase(sortOrder)) {
                sort = Sort.by(Sort.Direction.ASC, sortField);
            } else if ("desc".equalsIgnoreCase(sortOrder)) {
                sort = Sort.by(Sort.Direction.DESC, sortField);
            }
        }
        return sort;
    }

    public static Sort generateSort(String key, String order) {
        return generateSort(new SortBy(key, order));
    }
}
