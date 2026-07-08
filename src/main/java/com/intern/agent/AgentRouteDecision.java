package com.intern.agent;

import com.intern.model.entity.AiAgent;

public record AgentRouteDecision(AiAgent agent, String matchedKeyword, boolean fallback, boolean handoffRecommended) {
}
