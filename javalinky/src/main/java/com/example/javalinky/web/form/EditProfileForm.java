package com.example.javalinky.web.form;

import jakarta.validation.constraints.Size;

public record EditProfileForm(
    @Size(max = 64, message = "真名最长 64 个字符") String name,
    @Size(max = 64, message = "所在地最长 64 个字符") String location,
    @Size(max = 2000, message = "自我介绍最长 2000 个字符") String aboutMe
) {}
