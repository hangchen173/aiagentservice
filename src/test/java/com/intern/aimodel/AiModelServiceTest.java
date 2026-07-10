package com.intern.aimodel;

import com.intern.mapper.AiModelMapper;
import com.intern.mapper.ModelCallLogMapper;
import com.intern.model.entity.AiAgent;
import com.intern.model.entity.AiModel;
import com.intern.model.entity.ModelCallLog;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModelServiceTest {
    @Test
    void writesSuccessLogAndReturnsGatewayResponse() {
        AiModelMapper aiModelMapper = mock(AiModelMapper.class);
        ModelCallLogMapper logMapper = mock(ModelCallLogMapper.class);
        AiChatGateway gateway = mock(AiChatGateway.class);
        AiModel model = model("qwen-test");
        AiAgent agent = agent();

        when(aiModelMapper.selectOne(any())).thenReturn(model);
        when(gateway.complete(eq(model), any(), eq("价格怎么收费"))).thenReturn("模型回复");

        AiModelService service = new AiModelService(aiModelMapper, logMapper, gateway, Runnable::run, "qwen-default", 20, 600);
        String response = service.complete(9L, agent, "价格怎么收费");

        assertThat(response).isEqualTo("模型回复");
        ModelCallLog log = capturedLog(logMapper);
        assertThat(log.getSessionId()).isEqualTo(9L);
        assertThat(log.getModelName()).isEqualTo("qwen-test");
        assertThat(log.getAgentCode()).isEqualTo("presales");
        assertThat(log.getPromptPreview()).contains("用户问题：价格怎么收费");
        assertThat(log.getResponsePreview()).isEqualTo("模型回复");
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getLatencyMs()).isNotNull();
    }

    @Test
    void writesFailedLogAndReturnsFallbackWhenGatewayFails() {
        AiModelMapper aiModelMapper = mock(AiModelMapper.class);
        ModelCallLogMapper logMapper = mock(ModelCallLogMapper.class);
        AiChatGateway gateway = mock(AiChatGateway.class);
        AiModel model = model("qwen-test");

        when(aiModelMapper.selectOne(any())).thenReturn(model);
        when(gateway.complete(any(), any(), any())).thenThrow(new RuntimeException("模型不可用"));

        AiModelService service = new AiModelService(aiModelMapper, logMapper, gateway, Runnable::run, "qwen-default", 20, 600);
        String response = service.complete(10L, agent(), "帮我转人工");

        assertThat(response).isEqualTo("我已记录你的问题，当前 AI 模型暂时不可用，建议转人工客服继续处理。");
        ModelCallLog log = capturedLog(logMapper);
        assertThat(log.getStatus()).isEqualTo("FAILED");
        assertThat(log.getResponsePreview()).isEqualTo(response);
        assertThat(log.getErrorMessage()).isEqualTo("模型不可用");
    }

    @Test
    void usesDefaultModelWhenNoModelIsEnabled() {
        AiModelMapper aiModelMapper = mock(AiModelMapper.class);
        ModelCallLogMapper logMapper = mock(ModelCallLogMapper.class);
        AiChatGateway gateway = mock(AiChatGateway.class);
        AiAgent agent = agent();

        when(aiModelMapper.selectOne(any())).thenReturn(null);
        when(gateway.complete(any(), any(), eq("你好"))).thenReturn("默认模型回复");

        AiModelService service = new AiModelService(aiModelMapper, logMapper, gateway, Runnable::run, "qwen-default", 20, 600);
        String response = service.complete(11L, agent, "你好");

        assertThat(response).isEqualTo("默认模型回复");
        ArgumentCaptor<AiModel> modelCaptor = ArgumentCaptor.forClass(AiModel.class);
        verify(gateway).complete(modelCaptor.capture(), any(), eq("你好"));
        assertThat(modelCaptor.getValue().getModelName()).isEqualTo("qwen-default");
        assertThat(modelCaptor.getValue().getMaxTokens()).isEqualTo(600);
        assertThat(capturedLog(logMapper).getModelName()).isEqualTo("qwen-default");
    }

    @Test
    void capsEnabledModelMaxTokensBeforeGatewayCall() {
        AiModelMapper aiModelMapper = mock(AiModelMapper.class);
        ModelCallLogMapper logMapper = mock(ModelCallLogMapper.class);
        AiChatGateway gateway = mock(AiChatGateway.class);
        AiModel model = model("qwen-test");
        model.setMaxTokens(1200);

        when(aiModelMapper.selectOne(any())).thenReturn(model);
        when(gateway.complete(any(), any(), any())).thenReturn("短回复");

        AiModelService service = new AiModelService(aiModelMapper, logMapper, gateway, Runnable::run, "qwen-default", 20, 400);
        service.complete(12L, agent(), "你好");

        ArgumentCaptor<AiModel> modelCaptor = ArgumentCaptor.forClass(AiModel.class);
        verify(gateway).complete(modelCaptor.capture(), any(), any());
        assertThat(modelCaptor.getValue().getMaxTokens()).isEqualTo(400);
    }

    @Test
    void streamsGatewayChunksAndWritesSuccessLog() {
        AiModelMapper aiModelMapper = mock(AiModelMapper.class);
        ModelCallLogMapper logMapper = mock(ModelCallLogMapper.class);
        AiChatGateway gateway = mock(AiChatGateway.class);
        AiModel model = model("qwen-test");

        when(aiModelMapper.selectOne(any())).thenReturn(model);
        when(gateway.stream(any(), any(), eq("你好"))).thenReturn(Flux.just("第一段", "第二段"));

        AiModelService service = new AiModelService(aiModelMapper, logMapper, gateway, Runnable::run, "qwen-default", 20, 600);
        List<String> chunks = service.stream(13L, agent(), "你好").collectList().block();

        assertThat(chunks).containsExactly("第一段", "第二段");
        ModelCallLog log = capturedLog(logMapper);
        assertThat(log.getStatus()).isEqualTo("SUCCESS");
        assertThat(log.getResponsePreview()).isEqualTo("第一段第二段");
    }

    @Test
    void streamsFallbackAndWritesFailedLogWhenGatewayStreamFails() {
        AiModelMapper aiModelMapper = mock(AiModelMapper.class);
        ModelCallLogMapper logMapper = mock(ModelCallLogMapper.class);
        AiChatGateway gateway = mock(AiChatGateway.class);
        AiModel model = model("qwen-test");

        when(aiModelMapper.selectOne(any())).thenReturn(model);
        when(gateway.stream(any(), any(), any())).thenReturn(Flux.error(new RuntimeException("stream broken")));

        AiModelService service = new AiModelService(aiModelMapper, logMapper, gateway, Runnable::run, "qwen-default", 20, 600);
        List<String> chunks = service.stream(14L, agent(), "你好").collectList().block();

        assertThat(chunks).containsExactly("我已记录你的问题，当前 AI 模型暂时不可用，建议转人工客服继续处理。");
        ModelCallLog log = capturedLog(logMapper);
        assertThat(log.getStatus()).isEqualTo("FAILED");
        assertThat(log.getErrorMessage()).isEqualTo("stream broken");
    }

    private ModelCallLog capturedLog(ModelCallLogMapper logMapper) {
        ArgumentCaptor<ModelCallLog> logCaptor = ArgumentCaptor.forClass(ModelCallLog.class);
        verify(logMapper).insert(logCaptor.capture());
        return logCaptor.getValue();
    }

    private AiModel model(String modelName) {
        AiModel model = new AiModel();
        model.setProvider("DASHSCOPE");
        model.setModelName(modelName);
        model.setTemperature(BigDecimal.valueOf(0.7));
        model.setMaxTokens(1200);
        model.setEnabled(true);
        return model;
    }

    private AiAgent agent() {
        AiAgent agent = new AiAgent();
        agent.setCode("presales");
        agent.setName("售前咨询智能体");
        agent.setPrompt("你是售前咨询智能体");
        return agent;
    }
}
