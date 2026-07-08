package com.intern.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("handoff_records")
public class HandoffRecord extends BaseEntity {
    private Long sessionId;
    private String fromAiAgentCode;
    private Long toAgentId;
    private String reason;
    private String status;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getFromAiAgentCode() {
        return fromAiAgentCode;
    }

    public void setFromAiAgentCode(String fromAiAgentCode) {
        this.fromAiAgentCode = fromAiAgentCode;
    }

    public Long getToAgentId() {
        return toAgentId;
    }

    public void setToAgentId(Long toAgentId) {
        this.toAgentId = toAgentId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
