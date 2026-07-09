package com.intern.chat;

import jakarta.validation.constraints.Size;

public record CreateSessionRequest(@Size(max = 80, message = "会话标题最多 80 个字符") String title) {
}
