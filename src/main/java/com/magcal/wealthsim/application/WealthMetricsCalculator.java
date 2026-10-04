package com.magcal.wealthsim.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.magcal.wealthsim.domain.AgentState;
import com.magcal.wealthsim.domain.Money;
import com.magcal.wealthsim.domain.Population;

public final class WealthMetricsCalculator {
    public static final int RATIO_SCALE = 10;
    public static final RoundingMode RATIO_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO_RATIO = BigDecimal.ZERO.setScale(RATIO_SCALE);
    private static final BigDecimal ONE_RATIO = BigDecimal.ONE.setScale(RATIO_SCALE);

    public WealthDistributionMetrics calculate(SimulationResult result) {
        Objects.requireNonNull(result, "result");
        return calculate(result.finalPopulation());
    }

    public WealthDistributionMetrics calculate(Population population) {
        Objects.requireNonNull(population, "population");
        List<Money> sorted = new ArrayList<>(population.agents().stream()
                .map(AgentState::wealth)
                .toList());
        sorted.sort(Comparator.naturalOrder());
        int size = sorted.size();
        if (size == 0) {
            throw new IllegalArgumentException("Metrics require a non-empty population");
        }

        Money total = sorted.stream().reduce(Money.ZERO, Money::add);
        Money mean = Money.of(total.amount().divide(
                BigDecimal.valueOf(size), Money.SCALE, Money.ROUNDING));
        Money median = median(sorted);
        BigDecimal gini = gini(sorted, total);

        return new WealthDistributionMetrics(
                size,
                total,
                mean,
                median,
                sorted.getFirst(),
                sorted.getLast(),
                gini,
                share(sorted, total, segmentSize(size, 1, 100), true),
                share(sorted, total, segmentSize(size, 10, 100), true),
                share(sorted, total, segmentSize(size, 50, 100), false));
    }

    private Money median(List<Money> sorted) {
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return Money.of(sorted.get(middle - 1).amount()
                .add(sorted.get(middle).amount())
                .divide(BigDecimal.TWO, Money.SCALE, Money.ROUNDING));
    }

    private BigDecimal gini(List<Money> sorted, Money total) {
        if (total.isZero()) {
            return ZERO_RATIO;
        }
        BigDecimal weightedSum = BigDecimal.ZERO;
        for (int index = 0; index < sorted.size(); index++) {
            weightedSum = weightedSum.add(
                    sorted.get(index).amount().multiply(BigDecimal.valueOf(index + 1L)));
        }
        BigDecimal size = BigDecimal.valueOf(sorted.size());
        BigDecimal firstTerm = weightedSum.multiply(BigDecimal.TWO)
                .divide(size.multiply(total.amount()), RATIO_SCALE, RATIO_ROUNDING);
        BigDecimal secondTerm = size.add(BigDecimal.ONE)
                .divide(size, RATIO_SCALE, RATIO_ROUNDING);
        BigDecimal value = firstTerm.subtract(secondTerm).setScale(RATIO_SCALE, RATIO_ROUNDING);
        return value.max(ZERO_RATIO).min(ONE_RATIO);
    }

    private BigDecimal share(List<Money> sorted, Money total, int count, boolean top) {
        if (total.isZero()) {
            return ZERO_RATIO;
        }
        int start = top ? sorted.size() - count : 0;
        int end = top ? sorted.size() : count;
        BigDecimal segmentWealth = BigDecimal.ZERO;
        for (int index = start; index < end; index++) {
            segmentWealth = segmentWealth.add(sorted.get(index).amount());
        }
        return segmentWealth.divide(total.amount(), RATIO_SCALE, RATIO_ROUNDING);
    }

    private int segmentSize(int populationSize, int numerator, int denominator) {
        return Math.max(1, (populationSize * numerator + denominator - 1) / denominator);
    }
}
