package com.intern.aimodel;

import com.intern.model.entity.AiModel;
import org.springframework.stereotype.Component;

@Component
public class DemoAiChatGateway {
    public String complete(AiModel model, String systemPrompt, String userMessage) {
        String lower = userMessage == null ? "" : userMessage.toLowerCase();
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
