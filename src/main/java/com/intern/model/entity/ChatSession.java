package com.intern.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("chat_sessions")
public class ChatSession extends BaseEntity {
    private Long visitorId;
    private Long assignedAgentId;
    private String title;
    private String status;
    private String currentAiAgentCode;

    public Long getVisitorId() {
        return visitorId;
    }

    public void setVisitorId(Long visitorId) {
        this.visitorId = visitorId;
    }

    public Long getAssignedAgentId() {
        return assignedAgentId;
    }

    public void setAssignedAgentId(Long assignedAgentId) {
        this.assignedAgentId = assignedAgentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentAiAgentCode() {
        return currentAiAgentCode;
    }

    public void setCurrentAiAgentCode(String currentAiAgentCode) {
        this.currentAiAgentCode = currentAiAgentCode;
    }
}
