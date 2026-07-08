package com.intern.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("model_call_logs")
public class ModelCallLog extends BaseEntity {
    private Long sessionId;
    private String modelName;
    private String agentCode;
    private String promptPreview;
    private String responsePreview;
    private String status;
    private Long latencyMs;
    private String errorMessage;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getPromptPreview() {
        return promptPreview;
    }

    public void setPromptPreview(String promptPreview) {
        this.promptPreview = promptPreview;
    }

    public String getResponsePreview() {
        return responsePreview;
    }

    public void setResponsePreview(String responsePreview) {
        this.responsePreview = responsePreview;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
