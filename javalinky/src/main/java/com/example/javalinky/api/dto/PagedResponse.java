package com.example.javalinky.api.dto;

import com.example.javalinky.db.Page;
import java.util.List;
import java.util.function.Function;

/** 集合资源的统一响应格式 */
public record PagedResponse<T>(
    List<T> items,
    long total,
    int page,
    int pageSize,
    int totalPages,
    String prev,      // 没有上一页时是 null
    String next
) {
    public static <E, T> PagedResponse<T> of(Page<E> source,
                                             Function<E, T> mapper,
                                             String baseUrl) {
        return new PagedResponse<>(
            source.items().stream().map(mapper).toList(),
            source.total(),
            source.page(),
            source.size(),
            source.totalPages(),
            source.hasPrevious() ? baseUrl + "?page=" + source.previousPage() : null,
            source.hasNext()     ? baseUrl + "?page=" + source.nextPage()     : null);
    }
}
