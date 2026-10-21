package com.example.javalinky.web.form;

import jakarta.validation.constraints.*;

public record RegisterForm(
    @NotBlank(message = "请填写邮箱")
    @Size(max = 64, message = "邮箱最长 64 个字符")
    @Email(message = "邮箱格式不正确")
    String email,

    @NotBlank(message = "请填写用户名")
    @Size(max = 64, message = "用户名最长 64 个字符")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.]*$",
             message = "用户名必须以字母开头，只能包含字母、数字、点和下划线")
    String username,

    @NotBlank(message = "请填写密码")
    @Size(min = 8, max = 72, message = "密码长度需在 8 到 72 之间")
    String password,

    @NotBlank(message = "请再输入一次密码")
    String password2
) {
    @AssertTrue(message = "两次输入的密码不一致")
    public boolean isPasswordsMatch() {
        return password != null && password.equals(password2);
    }
}
