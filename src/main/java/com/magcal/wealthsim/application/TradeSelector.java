package com.magcal.wealthsim.application;

import java.util.List;
import java.util.Objects;

import com.magcal.wealthsim.domain.AgentId;

public final class TradeSelector {
    private final RandomSource randomSource;

    public TradeSelector(RandomSource randomSource) {
        this.randomSource = Objects.requireNonNull(randomSource, "randomSource");
    }

    public TradeSelection next(List<AgentId> population) {
        Objects.requireNonNull(population, "population");
        if (population.size() < 2) {
            throw new IllegalArgumentException("Trade selection requires at least two agents");
        }
        int leftIndex = randomSource.nextInt(population.size());
        int rightIndex = randomSource.nextInt(population.size() - 1);
        if (rightIndex >= leftIndex) {
            rightIndex++;
        }
        AgentId left = population.get(leftIndex);
        AgentId right = population.get(rightIndex);
        AgentId winner = randomSource.nextBoolean() ? left : right;
        return new TradeSelection(left, right, winner);
    }
}
