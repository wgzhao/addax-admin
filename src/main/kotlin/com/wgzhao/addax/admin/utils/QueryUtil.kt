package com.wgzhao.addax.admin.utils

import com.wgzhao.addax.admin.dto.SortBy
import org.springframework.data.domain.Sort

/**
 * 查询工具类。
 * 提供生成 Spring Data JPA Sort 对象的静态方法，支持按字段和排序方式动态构建排序条件。
 */
object QueryUtil {
    /**
     * 根据 SortBy 对象生成排序条件。
     * @param sortBy 排序参数对象，包含字段名和排序方式
     * @return Sort 排序条件对象
     */
    fun generateSort(sortBy: SortBy?): Sort {
        var sort = Sort.unsorted()
        if (sortBy != null && sortBy.key != null && !sortBy.key.isEmpty()) {
            val sortOrder: String? = sortBy.order
            val sortField: String = sortBy.key
            if ("asc".equals(sortOrder, ignoreCase = true)) {
                sort = Sort.by(Sort.Direction.ASC, sortField)
            } else if ("desc".equals(sortOrder, ignoreCase = true)) {
                sort = Sort.by(Sort.Direction.DESC, sortField)
            }
        }
        return sort
    }

    /**
     * 根据字段名和排序方式生成排序条件。
     * @param key 字段名
     * @param order 排序方式（asc/desc）
     * @return Sort 排序条件对象
     */
    fun generateSort(key: String?, order: String?): Sort {
        return generateSort(SortBy(key, order))
    }
}
