package com.intern.aimodel;

import com.intern.model.entity.AiModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class SpringAiChatGateway implements AiChatGateway {
    private static final String BASE_SYSTEM_PROMPT =
            "你是 NexusMind 多智能体客服中枢中的专业客服智能体。请使用中文，回答要清晰、友好、可执行。";

    private final String deepseekApiKey;
    private final String dashscopeApiKey;
    private final ChatClient textChatClient;
    private final ChatClient visionChatClient;
    private final DemoAiChatGateway demoAiChatGateway;
    private final Duration streamChunkDelay;

    public SpringAiChatGateway(
            @Value("${nexusmind.ai.deepseek-api-key:}") String deepseekApiKey,
            @Value("${nexusmind.ai.dashscope-api-key:}") String dashscopeApiKey,
            @Value("${nexusmind.ai.demo-stream-chunk-delay-millis:80}") long demoStreamChunkDelayMillis,
            ChatClient.Builder chatClientBuilder,
            @Qualifier("visionChatClient") ChatClient visionChatClient,
            DemoAiChatGateway demoAiChatGateway) {
        this.deepseekApiKey = deepseekApiKey;
        this.dashscopeApiKey = dashscopeApiKey;
        this.textChatClient = chatClientBuilder.build();
        this.visionChatClient = visionChatClient;
        this.demoAiChatGateway = demoAiChatGateway;
        this.streamChunkDelay = Duration.ofMillis(demoStreamChunkDelayMillis);
    }

    @Override
    public String complete(AiModel model, String systemPrompt, String userMessage) {
        if (!isConfigured(deepseekApiKey)) {
            return demoAiChatGateway.complete(model, systemPrompt, userMessage);
        }

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model.getModelName())
                .temperature(model.getTemperature() == null ? null : model.getTemperature().doubleValue())
                .maxTokens(model.getMaxTokens())
                .build();

        return textChatClient.prompt()
                .system(BASE_SYSTEM_PROMPT + "\n\n" + systemPrompt)
                .user(userMessage)
                .options(options)
                .call()
                .content();
    }

    @Override
    public String completeWithImage(AiModel model, String systemPrompt, String userMessage, ImageInput image) {
        if (!isConfigured(dashscopeApiKey)) {
            return demoAiChatGateway.completeWithImage(model, systemPrompt, userMessage, image);
        }

        ByteArrayResource imageResource = new ByteArrayResource(image.data()) {
            @Override
            public String getFilename() {
                return image.filename();
            }
        };
        return visionChatClient.prompt()
                .system(BASE_SYSTEM_PROMPT + "\n\n" + systemPrompt)
                .user(user -> user.text(userMessage)
                        .media(MimeTypeUtils.parseMimeType(image.contentType()), imageResource))
                .options(optionsFor(model))
                .call()
                .content();
    }

    @Override
    public Flux<String> stream(AiModel model, String systemPrompt, String userMessage) {
        if (!isConfigured(deepseekApiKey)) {
            return Flux.fromIterable(splitForDemoStream(demoAiChatGateway.complete(model, systemPrompt, userMessage)))
                    .delayElements(streamChunkDelay);
        }

        OpenAiChatOptions options = optionsFor(model);

        return textChatClient.prompt()
                .system(BASE_SYSTEM_PROMPT + "\n\n" + systemPrompt)
                .user(userMessage)
                .options(options)
                .stream()
                .content();
    }

    private OpenAiChatOptions optionsFor(AiModel model) {
        return OpenAiChatOptions.builder()
                .model(model.getModelName())
                .temperature(model.getTemperature() == null ? null : model.getTemperature().doubleValue())
                .maxTokens(model.getMaxTokens())
                .build();
    }

    private boolean isConfigured(String key) {
        return key != null && !key.isBlank() && !"nexusmind-demo-key".equals(key);
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
