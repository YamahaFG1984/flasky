package com.example.javalinky.db;

import java.util.ArrayList;
import java.util.List;

/** 一页数据 + 分页元信息。相当于 Flask-SQLAlchemy 的 Pagination。 */
public record Page<T>(List<T> items, int page, int size, long total) {

    public static <T> Page<T> of(List<T> items, int page, int size, long total) {
        return new Page<>(items, page, size, total);
    }

    public static <T> Page<T> empty(int size) {
        return new Page<>(List.of(), 1, size, 0);
    }

    public int totalPages() {
        return total == 0 ? 1 : (int) Math.ceil((double) total / size);
    }

    public boolean hasPrevious() { return page > 1; }
    public boolean hasNext()     { return page < totalPages(); }
    public int previousPage()    { return Math.max(1, page - 1); }
    public int nextPage()        { return Math.min(totalPages(), page + 1); }
    public boolean isEmpty()     { return items.isEmpty(); }

    /**
     * 要显示哪些页码。0 表示省略号。
     * 相当于 Flask-SQLAlchemy 的 iter_pages()。
     * 例如当前第 7 页、共 20 页 → [1, 0, 5, 6, 7, 8, 9, 0, 20]
     */
    public List<Integer> pageNumbers() {
        int last = totalPages();
        int window = 2;                     // 当前页左右各显示几个
        List<Integer> result = new ArrayList<>();
        int previous = 0;

        for (int i = 1; i <= last; i++) {
            boolean show = i <= 1                       // 第一页总是显示
                        || i > last - 1                  // 最后一页总是显示
                        || Math.abs(i - page) <= window; // 当前页附近
            if (!show) continue;
            if (previous != 0 && i - previous > 1) result.add(0);  // 中间有断档 → 省略号
            result.add(i);
            previous = i;
        }
        return result;
    }

    /** 把页码参数规整到合法范围，防止 ?page=-5 或 ?page=99999 */
    public static int clampPage(Integer requested) {
        if (requested == null || requested < 1) return 1;
        return Math.min(requested, 100_000);
    }
}
