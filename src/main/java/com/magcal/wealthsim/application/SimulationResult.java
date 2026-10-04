package com.magcal.wealthsim.application;

import java.util.Objects;

import com.magcal.wealthsim.domain.Money;
import com.magcal.wealthsim.domain.Population;

public record SimulationResult(
        SimulationConfiguration configuration,
        int executedTradeCount,
        Population finalPopulation) {
    public SimulationResult {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(finalPopulation, "finalPopulation");
        if (executedTradeCount < 0 || executedTradeCount > configuration.tradeCount()) {
            throw new IllegalArgumentException("Executed trade count is outside the configured range");
        }
    }

    public Money totalWealth() {
        return finalPopulation.totalWealth();
    }
}
