package com.example.javalinky.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NameForm(
    @NotBlank(message = "请填写名字")
    @Size(max = 64, message = "名字最长 64 个字符")
    String name
) {}
