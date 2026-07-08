package com.intern.ws;

public class WsMessage {
    private String type;
    private Long sessionId;
    private String content;

    public WsMessage() {
    }

    public WsMessage(String type, Long sessionId, String content) {
        this.type = type;
        this.sessionId = sessionId;
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
