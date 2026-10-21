package com.example.javalinky.web;

import java.util.List;
import java.util.Map;

/**
 * 传给模板的表单状态。
 * values：要回填到 input 里的值（键是字段名）
 * errors：字段名 → 错误信息
 */
public record FormView(Map<String, String> values, Map<String, List<String>> errors) {

    public static FormView empty() {
        return new FormView(Map.of(), Map.of());
    }

    /** 模板里取回填值，没有就返回空串（不会是 null，模板省一层判断） */
    public String value(String field) {
        return values.getOrDefault(field, "");
    }

    public boolean hasError(String field) {
        return errors.containsKey(field);
    }

    /** 该字段的第一条错误，没有则返回 null */
    public String error(String field) {
        List<String> list = errors.get(field);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    public boolean ok() {
        return errors.isEmpty();
    }
}
