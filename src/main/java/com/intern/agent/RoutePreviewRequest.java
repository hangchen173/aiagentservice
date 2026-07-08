package com.intern.agent;

import jakarta.validation.constraints.NotBlank;

public record RoutePreviewRequest(@NotBlank String content) {
}
