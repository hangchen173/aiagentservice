package com.intern.aimodel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.intern.mapper.AiModelMapper;
import com.intern.mapper.ModelCallLogMapper;
import com.intern.model.entity.AiAgent;
import com.intern.model.entity.AiModel;
import com.intern.model.entity.ModelCallLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Flux;

@Service
public class AiModelService {
    private final AiModelMapper aiModelMapper;
    private final ModelCallLogMapper modelCallLogMapper;
    private final AiChatGateway aiChatGateway;
    private final String defaultModel;
    private final Duration timeout;
    private final int maxTokensLimit;
    private final Executor aiModelExecutor;

    public AiModelService(
            AiModelMapper aiModelMapper,
            ModelCallLogMapper modelCallLogMapper,
            AiChatGateway aiChatGateway,
            @Qualifier("aiModelExecutor") Executor aiModelExecutor,
            @Value("${nexusmind.ai.default-model}") String defaultModel,
            @Value("${nexusmind.ai.timeout-seconds:20}") long timeoutSeconds,
            @Value("${nexusmind.ai.max-tokens-limit:600}") int maxTokensLimit) {
        this.aiModelMapper = aiModelMapper;
        this.modelCallLogMapper = modelCallLogMapper;
        this.aiChatGateway = aiChatGateway;
        this.aiModelExecutor = aiModelExecutor;
        this.defaultModel = defaultModel;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxTokensLimit = maxTokensLimit;
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
        String systemPrompt = compactPrompt(agent.getPrompt());
        String prompt = systemPrompt + "\n\n用户问题：" + userMessage;
        Instant startedAt = Instant.now();
        ModelCallLog log = new ModelCallLog();
        log.setSessionId(sessionId);
        log.setModelName(model.getModelName());
        log.setAgentCode(agent.getCode());
        log.setPromptPreview(preview(prompt));

        try {
            String response = CompletableFuture
                    .supplyAsync(() -> aiChatGateway.complete(model, systemPrompt, userMessage), aiModelExecutor)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            log.setStatus("SUCCESS");
            log.setResponsePreview(preview(response));
            log.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
            modelCallLogMapper.insert(log);
            return response;
        } catch (Exception ex) {
            String fallback = "我已记录你的问题，当前 AI 模型暂时不可用，建议转人工客服继续处理。";
            log.setStatus("FAILED");
            log.setResponsePreview(fallback);
            log.setErrorMessage(preview(errorMessage(ex)));
            log.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
            modelCallLogMapper.insert(log);
            return fallback;
        }
    }

    public Flux<String> stream(Long sessionId, AiAgent agent, String userMessage) {
        AiModel model = activeModel();
        String systemPrompt = compactPrompt(agent.getPrompt());
        String prompt = systemPrompt + "\n\n用户问题：" + userMessage;
        Instant startedAt = Instant.now();
        ModelCallLog log = new ModelCallLog();
        log.setSessionId(sessionId);
        log.setModelName(model.getModelName());
        log.setAgentCode(agent.getCode());
        log.setPromptPreview(preview(prompt));
        StringBuilder response = new StringBuilder();
        AtomicBoolean logged = new AtomicBoolean(false);

        return aiChatGateway.stream(model, systemPrompt, userMessage)
                .timeout(timeout)
                .doOnNext(response::append)
                .doOnComplete(() -> {
                    if (logged.compareAndSet(false, true)) {
                        log.setStatus("SUCCESS");
                        log.setResponsePreview(preview(response.toString()));
                        log.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
                        modelCallLogMapper.insert(log);
                    }
                })
                .onErrorResume(ex -> {
                    if (logged.compareAndSet(false, true)) {
                        String fallback = "我已记录你的问题，当前 AI 模型暂时不可用，建议转人工客服继续处理。";
                        log.setStatus("FAILED");
                        log.setResponsePreview(fallback);
                        log.setErrorMessage(preview(errorMessage(ex)));
                        log.setLatencyMs(Duration.between(startedAt, Instant.now()).toMillis());
                        modelCallLogMapper.insert(log);
                        return Flux.just(fallback);
                    }
                    return Flux.empty();
                });
    }

    private AiModel activeModel() {
        AiModel model = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getEnabled, true)
                .orderByAsc(AiModel::getId)
                .last("limit 1"));
        if (model != null) {
            capMaxTokens(model);
            return model;
        }
        AiModel fallback = new AiModel();
        fallback.setProvider("DASHSCOPE");
        fallback.setModelName(defaultModel);
        fallback.setTemperature(BigDecimal.valueOf(0.7));
        fallback.setMaxTokens(Math.min(600, maxTokensLimit));
        fallback.setEnabled(true);
        return fallback;
    }

    private void capMaxTokens(AiModel model) {
        if (model.getMaxTokens() == null || model.getMaxTokens() > maxTokensLimit) {
            model.setMaxTokens(maxTokensLimit);
        }
    }

    private String compactPrompt(String prompt) {
        return prompt + "\n\n请优先用 3 到 5 句话回答；需要转人工或创建工单时说明原因即可，避免输出虚构工单号。";
    }

    private String errorMessage(Throwable ex) {
        if (ex instanceof TimeoutException) {
            return "AI 模型响应超时：" + timeout.toSeconds() + " 秒";
        }
        Throwable cause = ex.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return ex.getMessage();
    }

    private String preview(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
