package com.example.javalinky.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentForm(
    @NotBlank(message = "评论不能为空")
    @Size(max = 2000, message = "评论最长 2000 个字符")
    String body
) {}
