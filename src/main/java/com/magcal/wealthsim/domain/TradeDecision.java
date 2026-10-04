package com.magcal.wealthsim.domain;

import java.util.Objects;

public record TradeDecision(AgentId winner, AgentId loser, Money stake) {
    public TradeDecision {
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(loser, "loser");
        Objects.requireNonNull(stake, "stake");
    }
}
