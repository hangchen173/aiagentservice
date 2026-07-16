package com.intern.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VisionAiConfig {
    @Bean
    @Qualifier("visionChatClient")
    public ChatClient visionChatClient(
            @Value("${nexusmind.ai.dashscope-api-key:}") String apiKey,
            @Value("${nexusmind.ai.dashscope-base-url}") String baseUrl,
            @Value("${nexusmind.ai.vision-model}") String modelName) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey == null || apiKey.isBlank() ? "nexusmind-demo-key" : apiKey)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).build())
                .build();
        return ChatClient.create(model);
    }
}
