package com.intern.aimodel;

import com.intern.model.entity.AiModel;

public interface AiChatGateway {
    String complete(AiModel model, String systemPrompt, String userMessage);
}
