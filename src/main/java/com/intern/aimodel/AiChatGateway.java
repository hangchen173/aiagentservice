package com.intern.aimodel;

import com.intern.model.entity.AiModel;
import reactor.core.publisher.Flux;

public interface AiChatGateway {
    String complete(AiModel model, String systemPrompt, String userMessage);

    default Flux<String> stream(AiModel model, String systemPrompt, String userMessage) {
        return Flux.just(complete(model, systemPrompt, userMessage));
    }
}
