package com.example.javalinky.web.form;

import jakarta.validation.constraints.*;

public record AdminEditProfileForm(
    @NotBlank(message = "请填写邮箱")
    @Size(max = 64) @Email(message = "邮箱格式不正确")
    String email,

    @NotBlank(message = "请填写用户名")
    @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.]*$",
             message = "用户名必须以字母开头，只能包含字母、数字、点和下划线")
    String username,

    boolean confirmed,

    @NotNull(message = "请选择角色")
    Long roleId,

    @Size(max = 64) String name,
    @Size(max = 64) String location,
    @Size(max = 2000) String aboutMe
) {}
