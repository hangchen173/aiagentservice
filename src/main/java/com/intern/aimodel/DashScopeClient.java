package com.intern.aimodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intern.model.entity.AiModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DashScopeClient {
    private static final String COMPATIBLE_CHAT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final String apiKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DashScopeClient(@Value("${nexusmind.ai.dashscope-api-key}") String apiKey, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(COMPATIBLE_CHAT_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public String complete(AiModel model, String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            return demoReply(model, prompt);
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model.getModelName());
            body.put("temperature", model.getTemperature());
            body.put("max_tokens", model.getMaxTokens());
            body.put("messages", List.of(
                    Map.of(
                            "role", "system",
                            "content", "你是 NexusMind 多智能体客服中枢中的专业客服智能体。请使用中文，回答要清晰、友好、可执行。"
                    ),
                    Map.of("role", "user", "content", prompt)
            ));

            String response = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
                throw new IllegalStateException("DashScope 返回为空：" + response);
            }
            return contentNode.asText();
        } catch (Exception ex) {
            throw new IllegalStateException("DashScope 调用失败：" + ex.getMessage(), ex);
        }
    }

    private String demoReply(AiModel model, String prompt) {
        String lower = prompt == null ? "" : prompt.toLowerCase();
        if (lower.contains("退款") || lower.contains("投诉") || lower.contains("人工")) {
            return "我理解你的诉求。这个问题建议转人工客服进一步核实，我也会同步生成工单，方便后续跟进处理。";
        }
        if (lower.contains("价格") || lower.contains("方案") || lower.contains("报价")) {
            return "NexusMind 支持多模型接入、智能体调度和人工流转。你可以先描述使用规模、接入渠道和预算区间，我会帮你整理合适方案。";
        }
        if (lower.contains("故障") || lower.contains("无法使用") || lower.contains("订单")) {
            return "请提供订单号、故障现象和出现时间。我会先帮你定位问题，必要时转给售后坐席继续处理。";
        }
        return "你好，我是 NexusMind 智能客服。已收到你的问题，我会结合当前场景给出建议；如果问题复杂，也可以为你转人工处理。";
    }
}
