package com.wgzhao.addax.admin.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable page serialization DTO to avoid returning PageImpl directly.
 * @param <T> element type
 */
@Setter
@Getter
public class PageResponse<T>
{
    private List<T> content;
    private int page;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean empty;

    public PageResponse() {}

    public PageResponse(List<T> content, int page, int pageSize, long totalElements, int totalPages, boolean empty)
    {
        this.content = content;
        this.page = page;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.empty = empty;
    }

    public static <T> PageResponse<T> from(Page<T> page)
    {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isEmpty()
        );
    }

}

