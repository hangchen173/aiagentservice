package com.intern.aimodel;

import com.intern.model.entity.AiModel;
import reactor.core.publisher.Flux;

public interface AiChatGateway {
    String complete(AiModel model, String systemPrompt, String userMessage);

    default String completeWithImage(AiModel model, String systemPrompt, String userMessage, ImageInput image) {
        throw new UnsupportedOperationException("当前 AI 网关不支持图像识别");
    }

    default Flux<String> stream(AiModel model, String systemPrompt, String userMessage) {
        return Flux.just(complete(model, systemPrompt, userMessage));
    }
}
