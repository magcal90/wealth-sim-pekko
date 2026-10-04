package com.magcal.wealthsim.domain;

import java.util.Objects;

public record AgentId(String value) implements Comparable<AgentId> {
    public AgentId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Agent ID must not be blank");
        }
    }

    @Override
    public int compareTo(AgentId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
