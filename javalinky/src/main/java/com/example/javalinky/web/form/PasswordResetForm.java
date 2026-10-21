package com.example.javalinky.web.form;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetForm(
    @NotBlank(message = "请填写新密码")
    @Size(min = 8, max = 72, message = "密码长度需在 8 到 72 之间")
    String password,

    @NotBlank(message = "请再输入一次新密码")
    String password2
) {
    @AssertTrue(message = "两次输入的密码不一致")
    public boolean isPasswordsMatch() {
        return password != null && password.equals(password2);
    }
}
