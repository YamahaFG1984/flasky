package com.example.javalinky.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.*;

public final class Forms {

    // Validator 是线程安全的，创建一次全局复用（创建开销不小）
    private static final Validator VALIDATOR =
        Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * 校验一个表单对象。
     * @return 字段名 → 该字段的全部错误信息。没有错误时返回空 Map。
     */
    public static <T> Map<String, List<String>> validate(T form) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (ConstraintViolation<T> v : VALIDATOR.validate(form)) {
            String field = v.getPropertyPath().toString();
            errors.computeIfAbsent(field, k -> new ArrayList<>()).add(v.getMessage());
        }
        return errors;
    }

    private Forms() {}
}
