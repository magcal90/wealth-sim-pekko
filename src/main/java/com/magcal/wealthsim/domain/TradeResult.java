package com.magcal.wealthsim.domain;

import java.util.Objects;

public record TradeResult(AgentState left, AgentState right, TradeDecision decision) {
    public TradeResult {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(decision, "decision");
    }
}
