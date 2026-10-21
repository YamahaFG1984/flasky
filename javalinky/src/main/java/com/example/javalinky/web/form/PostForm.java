package com.example.javalinky.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostForm(
    @NotBlank(message = "内容不能为空")
    @Size(max = 20000, message = "内容最长 20000 个字符")
    String body
) {}
