package com.magcal.wealthsim.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.magcal.wealthsim.domain.AgentId;
import com.magcal.wealthsim.domain.AgentState;
import com.magcal.wealthsim.domain.Money;
import com.magcal.wealthsim.domain.Population;

class WealthMetricsCalculatorTest {
    private final WealthMetricsCalculator calculator = new WealthMetricsCalculator();

    @Test
    void calculatesEqualDistribution() {
        WealthDistributionMetrics metrics = calculator.calculate(population("100", "100", "100", "100"));

        assertEquals(4, metrics.populationSize());
        assertEquals(Money.of("400"), metrics.totalWealth());
        assertEquals(Money.of("100"), metrics.meanWealth());
        assertEquals(Money.of("100"), metrics.medianWealth());
        assertEquals(Money.of("100"), metrics.minimumWealth());
        assertEquals(Money.of("100"), metrics.maximumWealth());
        assertRatio("0", metrics.giniCoefficient());
        assertRatio("0.25", metrics.topOnePercentShare());
        assertRatio("0.25", metrics.topTenPercentShare());
        assertRatio("0.50", metrics.bottomFiftyPercentShare());
    }

    @Test
    void calculatesKnownUnequalDistributionAndEvenMedian() {
        WealthDistributionMetrics metrics = calculator.calculate(population("0", "0", "0", "100"));

        assertEquals(Money.of("25"), metrics.meanWealth());
        assertEquals(Money.ZERO, metrics.medianWealth());
        assertRatio("0.75", metrics.giniCoefficient());
        assertRatio("1", metrics.topOnePercentShare());
        assertRatio("0", metrics.bottomFiftyPercentShare());
    }

    @Test
    void calculatesOddMedianSharesAndIgnoresInputOrder() {
        Population ordered = population("10", "20", "30", "40", "100");
        Population shuffled = population("100", "20", "40", "10", "30");

        WealthDistributionMetrics first = calculator.calculate(ordered);
        WealthDistributionMetrics second = calculator.calculate(shuffled);

        assertEquals(Money.of("30"), first.medianWealth());
        assertRatio("0.50", first.topTenPercentShare());
        assertRatio("0.30", first.bottomFiftyPercentShare());
        assertEquals(first, second);
    }

    @Test
    void allZeroPopulationHasZeroRatios() {
        WealthDistributionMetrics metrics = calculator.calculate(population("0", "0", "0"));

        assertRatio("0", metrics.giniCoefficient());
        assertRatio("0", metrics.topOnePercentShare());
        assertRatio("0", metrics.topTenPercentShare());
        assertRatio("0", metrics.bottomFiftyPercentShare());
    }

    @Test
    void topOnePercentUsesOneAgentForSmallPopulationAndInputIsNotMutated() {
        List<AgentState> input = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            input.add(new AgentState(new AgentId("A" + index), Money.of(Integer.toString(index))));
        }
        List<AgentState> before = List.copyOf(input);

        WealthDistributionMetrics metrics = calculator.calculate(new Population(input));

        assertRatio(new BigDecimal("10").divide(new BigDecimal("55"), 10, Money.ROUNDING).toPlainString(),
                metrics.topOnePercentShare());
        assertEquals(before, input);
    }

    @Test
    void populationRejectsEmptyMetricsInput() {
        assertThrows(IllegalArgumentException.class, () -> new Population(List.of()));
    }

    private Population population(String... values) {
        List<AgentState> agents = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            agents.add(new AgentState(new AgentId("A" + index), Money.of(values[index])));
        }
        return new Population(agents);
    }

    private void assertRatio(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
