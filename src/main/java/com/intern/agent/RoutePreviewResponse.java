package com.intern.agent;

public record RoutePreviewResponse(
        Long agentId,
        String agentCode,
        String agentName,
        String scenario,
        String matchedKeyword,
        boolean fallback,
        boolean handoffRecommended) {
    public static RoutePreviewResponse from(AgentRouteDecision decision) {
        return new RoutePreviewResponse(
                decision.agent().getId(),
                decision.agent().getCode(),
                decision.agent().getName(),
                decision.agent().getScenario(),
                decision.matchedKeyword(),
                decision.fallback(),
                decision.handoffRecommended()
        );
    }
}
