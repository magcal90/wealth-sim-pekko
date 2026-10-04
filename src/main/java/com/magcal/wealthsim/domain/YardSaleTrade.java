package com.magcal.wealthsim.domain;

import java.util.Objects;

public final class YardSaleTrade {
    public TradeDecision decide(
            AgentState left,
            AgentState right,
            StakePercentage stakePercentage,
            AgentId winner) {
        validate(left, right, stakePercentage, winner);
        Money poorerWealth = left.wealth().compareTo(right.wealth()) <= 0
                ? left.wealth()
                : right.wealth();
        AgentId loser = winner.equals(left.id()) ? right.id() : left.id();
        return new TradeDecision(winner, loser, stakePercentage.applyTo(poorerWealth));
    }

    public TradeResult execute(
            AgentState left,
            AgentState right,
            StakePercentage stakePercentage,
            AgentId winner) {
        TradeDecision decision = decide(left, right, stakePercentage, winner);
        AgentState updatedLeft = update(left, decision);
        AgentState updatedRight = update(right, decision);
        return new TradeResult(updatedLeft, updatedRight, decision);
    }

    private AgentState update(AgentState agent, TradeDecision decision) {
        if (agent.id().equals(decision.winner())) {
            return agent.withWealth(agent.wealth().add(decision.stake()));
        }
        Money updated = agent.wealth().subtract(decision.stake());
        if (updated.isNegative()) {
            throw new IllegalStateException("A valid Yard-Sale trade cannot produce negative wealth");
        }
        return agent.withWealth(updated);
    }

    private void validate(
            AgentState left,
            AgentState right,
            StakePercentage stakePercentage,
            AgentId winner) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(stakePercentage, "stakePercentage");
        Objects.requireNonNull(winner, "winner");
        if (left.id().equals(right.id())) {
            throw new IllegalArgumentException("Trade participants must be distinct");
        }
        if (!winner.equals(left.id()) && !winner.equals(right.id())) {
            throw new IllegalArgumentException("Winner must be a trade participant");
        }
    }
}
