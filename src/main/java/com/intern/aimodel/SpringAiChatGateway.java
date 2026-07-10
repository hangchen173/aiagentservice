package com.intern.aimodel;

import com.intern.model.entity.AiModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpringAiChatGateway implements AiChatGateway {
    private static final String BASE_SYSTEM_PROMPT =
            "你是 NexusMind 多智能体客服中枢中的专业客服智能体。请使用中文，回答要清晰、友好、可执行。";

    private final String apiKey;
    private final ChatClient chatClient;
    private final DemoAiChatGateway demoAiChatGateway;
    private final Duration streamChunkDelay;

    public SpringAiChatGateway(
            @Value("${nexusmind.ai.dashscope-api-key:}") String apiKey,
            @Value("${nexusmind.ai.demo-stream-chunk-delay-millis:80}") long demoStreamChunkDelayMillis,
            ChatClient.Builder chatClientBuilder,
            DemoAiChatGateway demoAiChatGateway) {
        this.apiKey = apiKey;
        this.chatClient = chatClientBuilder.build();
        this.demoAiChatGateway = demoAiChatGateway;
        this.streamChunkDelay = Duration.ofMillis(demoStreamChunkDelayMillis);
    }

    @Override
    public String complete(AiModel model, String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            return demoAiChatGateway.complete(model, systemPrompt, userMessage);
        }

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model.getModelName())
                .temperature(model.getTemperature() == null ? null : model.getTemperature().doubleValue())
                .maxTokens(model.getMaxTokens())
                .build();

        return chatClient.prompt()
                .system(BASE_SYSTEM_PROMPT + "\n\n" + systemPrompt)
                .user(userMessage)
                .options(options)
                .call()
                .content();
    }

    @Override
    public Flux<String> stream(AiModel model, String systemPrompt, String userMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            return Flux.fromIterable(splitForDemoStream(demoAiChatGateway.complete(model, systemPrompt, userMessage)))
                    .delayElements(streamChunkDelay);
        }

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model.getModelName())
                .temperature(model.getTemperature() == null ? null : model.getTemperature().doubleValue())
                .maxTokens(model.getMaxTokens())
                .build();

        return chatClient.prompt()
                .system(BASE_SYSTEM_PROMPT + "\n\n" + systemPrompt)
                .user(userMessage)
                .options(options)
                .stream()
                .content();
    }

    private List<String> splitForDemoStream(String response) {
        List<String> chunks = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return List.of("");
        }
        int start = 0;
        int chunkSize = 12;
        while (start < response.length()) {
            int end = Math.min(response.length(), start + chunkSize);
            chunks.add(response.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
