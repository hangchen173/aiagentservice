package com.intern.aimodel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intern.mapper.AiModelMapper;
import com.intern.mapper.ModelCallLogMapper;
import com.intern.model.entity.AiAgent;
import com.intern.model.entity.AiModel;
import com.intern.model.entity.ModelCallLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AiModelService {
    private final AiModelMapper aiModelMapper;
    private final ModelCallLogMapper modelCallLogMapper;
    private final AiChatGateway aiChatGateway;
    private final String defaultModel;

    public AiModelService(
            AiModelMapper aiModelMapper,
            ModelCallLogMapper modelCallLogMapper,
            AiChatGateway aiChatGateway,
            @Value("${nexusmind.ai.default-model}") String defaultModel) {
        this.aiModelMapper = aiModelMapper;
        this.modelCallLogMapper = modelCallLogMapper;
        this.aiChatGateway = aiChatGateway;
        this.defaultModel = defaultModel;
    }

    public List<AiModel> listModels() {
        return aiModelMapper.selectList(new LambdaQueryWrapper<AiModel>().orderByAsc(AiModel::getId));
    }

    public AiModel update(Long id, AiModel input) {
        input.setId(id);
        aiModelMapper.updateById(input);
        return aiModelMapper.selectById(id);
    }

    public List<ModelCallLog> listLogs() {
        return modelCallLogMapper.selectList(new LambdaQueryWrapper<ModelCallLog>()
                .orderByDesc(ModelCallLog::getCreatedAt)
                .last("limit 100"));
    }

    public String complete(Long sessionId, AiAgent agent, String userMessage) {
        AiModel model = activeModel();
        String systemPrompt = agent.getPrompt();
        String prompt = systemPrompt + "\n\n用户问题：" + userMessage;
        Instant startedAt = Instant.now();
        ModelCallLog log = new ModelCallLog();
        log.setSessionId(sessionId);
        log.setModelName(model.getModelName());
        log.setAgentCode(agent.getCode());
        log.setPromptPreview(preview(prompt));

        try {
            String response = aiChatGateway.complete(model, systemPrompt, userMessage);
            log.setStatus("SUCCESS");
            log.setResponsePreview(preview(response));
            log.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
            modelCallLogMapper.insert(log);
            return response;
        } catch (RuntimeException ex) {
            String fallback = "我已记录你的问题，当前 AI 模型暂时不可用，建议转人工客服继续处理。";
            log.setStatus("FAILED");
            log.setResponsePreview(fallback);
            log.setErrorMessage(preview(ex.getMessage()));
            log.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
            modelCallLogMapper.insert(log);
            return fallback;
        }
    }

    private AiModel activeModel() {
        AiModel model = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getEnabled, true)
                .orderByAsc(AiModel::getId)
                .last("limit 1"));
        if (model != null) {
            return model;
        }
        AiModel fallback = new AiModel();
        fallback.setProvider("DASHSCOPE");
        fallback.setModelName(defaultModel);
        fallback.setTemperature(BigDecimal.valueOf(0.7));
        fallback.setMaxTokens(1200);
        fallback.setEnabled(true);
        return fallback;
    }

    private String preview(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
