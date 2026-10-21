package com.example.javalinky.web.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginForm(
    @NotBlank(message = "请填写邮箱")
    @Size(max = 64, message = "邮箱最长 64 个字符")
    @Email(message = "邮箱格式不正确")
    String email,

    @NotBlank(message = "请填写密码")
    String password,

    boolean remember
) {}
