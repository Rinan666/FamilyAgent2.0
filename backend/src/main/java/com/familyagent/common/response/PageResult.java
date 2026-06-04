package com.familyagent.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 分页响应
 */
@Data
@AllArgsConstructor
public class PageResult<T> {

    private List<T> items;
    private long page;
    private long pageSize;
    private long total;
    private long totalPages;

    public static <T> PageResult<T> of(List<T> items, long page, long pageSize, long total) {
        long totalPages = (total + pageSize - 1) / pageSize;
        return new PageResult<>(items, page, pageSize, total, totalPages);
    }
}
