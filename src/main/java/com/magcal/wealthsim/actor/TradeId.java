package com.magcal.wealthsim.actor;

import java.util.Objects;

public record TradeId(String value) implements Comparable<TradeId> {
    public TradeId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Trade ID must not be blank");
        }
    }

    @Override
    public int compareTo(TradeId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
