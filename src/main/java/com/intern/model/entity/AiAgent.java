package com.intern.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_agents")
public class AiAgent extends BaseEntity {
    private String code;
    private String name;
    private String scenario;
    private String prompt;
    private Integer priority;
    private Boolean enabled;
    private String triggerKeywords;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getTriggerKeywords() {
        return triggerKeywords;
    }

    public void setTriggerKeywords(String triggerKeywords) {
        this.triggerKeywords = triggerKeywords;
    }
}
