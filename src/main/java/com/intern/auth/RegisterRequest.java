package com.intern.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名须为 3 到 32 位字母、数字或下划线")
        String username,
        @NotBlank(message = "请输入显示名称")
        @Size(max = 50, message = "显示名称不能超过 50 个字符")
        String displayName,
        @NotBlank(message = "请输入密码")
        @Size(min = 8, max = 72, message = "密码须为 8 到 72 个字符")
        String password) {
}
