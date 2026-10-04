package com.magcal.wealthsim.application;

import java.util.Objects;

import com.magcal.wealthsim.domain.AgentId;

public record TradeSelection(AgentId left, AgentId right, AgentId winner) {
    public TradeSelection {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(winner, "winner");
        if (left.equals(right)) {
            throw new IllegalArgumentException("Selected participants must be distinct");
        }
        if (!winner.equals(left) && !winner.equals(right)) {
            throw new IllegalArgumentException("Winner must be a selected participant");
        }
    }
}
