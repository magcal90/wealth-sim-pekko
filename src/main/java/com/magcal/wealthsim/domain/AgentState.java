package com.magcal.wealthsim.domain;

import java.util.Objects;

public record AgentState(AgentId id, Money wealth) {
    public AgentState {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(wealth, "wealth");
        if (wealth.isNegative()) {
            throw new IllegalArgumentException("Agent wealth must not be negative");
        }
    }

    public AgentState withWealth(Money newWealth) {
        return new AgentState(id, newWealth);
    }
}
