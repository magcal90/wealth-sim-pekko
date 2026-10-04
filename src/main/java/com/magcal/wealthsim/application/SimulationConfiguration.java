package com.magcal.wealthsim.application;

import java.util.Objects;

import com.magcal.wealthsim.domain.Money;
import com.magcal.wealthsim.domain.StakePercentage;

public record SimulationConfiguration(
        int populationSize,
        Money initialWealth,
        StakePercentage stakePercentage,
        int tradeCount,
        long seed) {
    public SimulationConfiguration {
        Objects.requireNonNull(initialWealth, "initialWealth");
        Objects.requireNonNull(stakePercentage, "stakePercentage");
        if (populationSize <= 0) {
            throw new IllegalArgumentException("Population size must be positive");
        }
        if (initialWealth.isNegative()) {
            throw new IllegalArgumentException("Initial wealth must not be negative");
        }
        if (tradeCount < 0) {
            throw new IllegalArgumentException("Trade count must not be negative");
        }
        if (tradeCount > 0 && populationSize < 2) {
            throw new IllegalArgumentException("A simulation with trades requires at least two agents");
        }
    }

    public Money initialTotalWealth() {
        return initialWealth.multiply(populationSize);
    }
}
