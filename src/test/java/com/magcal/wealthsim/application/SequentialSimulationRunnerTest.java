package com.magcal.wealthsim.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.magcal.wealthsim.domain.AgentId;
import com.magcal.wealthsim.domain.AgentState;
import com.magcal.wealthsim.domain.Money;
import com.magcal.wealthsim.domain.StakePercentage;

class SequentialSimulationRunnerTest {
    private final SequentialSimulationRunner runner = new SequentialSimulationRunner();

    @Test
    void executesRequestedTradesAndPreservesInvariants() {
        SimulationConfiguration configuration = configuration(10, 100, 12345);

        SimulationResult result = runner.run(configuration);

        assertEquals(100, result.executedTradeCount());
        assertEquals(10, result.finalPopulation().size());
        assertEquals(Money.of("1000"), result.totalWealth());
        assertTrue(result.finalPopulation().agents().stream().noneMatch(agent -> agent.wealth().isNegative()));
    }

    @Test
    void sameConfigurationReproducesFinalWealthByAgent() {
        SimulationConfiguration configuration = configuration(25, 500, 98765);

        assertEquals(wealthById(runner.run(configuration)), wealthById(runner.run(configuration)));
    }

    @Test
    void zeroTradesLeavesInitialPopulationUnchanged() {
        SimulationResult result = runner.run(configuration(4, 0, 10));

        assertEquals(0, result.executedTradeCount());
        result.finalPopulation().agents().forEach(agent -> assertEquals(Money.of("100"), agent.wealth()));
    }

    @Test
    void invalidConfigurationIsRejectedBeforeRun() {
        assertThrows(IllegalArgumentException.class, () -> configuration(1, 10, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationConfiguration(2, Money.ZERO,
                        StakePercentage.ofPercent("10"), -1, 1));
    }

    private SimulationConfiguration configuration(int populationSize, int trades, long seed) {
        return new SimulationConfiguration(populationSize, Money.of("100"),
                StakePercentage.ofPercent("10"), trades, seed);
    }

    private Map<AgentId, Money> wealthById(SimulationResult result) {
        return result.finalPopulation().agents().stream()
                .collect(Collectors.toMap(AgentState::id, AgentState::wealth));
    }
}
