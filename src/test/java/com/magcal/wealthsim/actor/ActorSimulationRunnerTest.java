package com.magcal.wealthsim.actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.magcal.wealthsim.application.SequentialSimulationRunner;
import com.magcal.wealthsim.application.SimulationConfiguration;
import com.magcal.wealthsim.application.SimulationResult;
import com.magcal.wealthsim.domain.AgentId;
import com.magcal.wealthsim.domain.AgentState;
import com.magcal.wealthsim.domain.Money;
import com.magcal.wealthsim.domain.StakePercentage;

class ActorSimulationRunnerTest {
    @Test
    void actorRunMatchesSequentialReferenceByAgentId() throws Exception {
        SimulationConfiguration configuration = configuration(20, 250, 12345);

        SimulationResult sequential = new SequentialSimulationRunner().run(configuration);
        SimulationResult actor = new ActorSimulationRunner().run(configuration);

        assertEquals(configuration.tradeCount(), actor.executedTradeCount());
        assertEquals(configuration.populationSize(), actor.finalPopulation().size());
        assertEquals(configuration.initialTotalWealth(), actor.totalWealth());
        assertTrue(actor.finalPopulation().agents().stream().noneMatch(agent -> agent.wealth().isNegative()));
        assertEquals(wealthById(sequential), wealthById(actor));
    }

    @Test
    void actorRunIsReproducible() throws Exception {
        SimulationConfiguration configuration = configuration(10, 50, 9876);

        assertEquals(
                wealthById(new ActorSimulationRunner().run(configuration)),
                wealthById(new ActorSimulationRunner().run(configuration)));
    }

    @Test
    void zeroTradeRunStillReturnsEveryInitializedAgent() throws Exception {
        SimulationConfiguration configuration = configuration(8, 0, 1);

        SimulationResult result = new ActorSimulationRunner().run(configuration);

        assertEquals(0, result.executedTradeCount());
        assertEquals(8, result.finalPopulation().size());
        assertEquals(Money.of("800"), result.totalWealth());
    }

    private SimulationConfiguration configuration(int population, int trades, long seed) {
        return new SimulationConfiguration(population, Money.of("100"),
                StakePercentage.ofPercent("10"), trades, seed);
    }

    private Map<AgentId, Money> wealthById(SimulationResult result) {
        return result.finalPopulation().agents().stream()
                .collect(Collectors.toMap(AgentState::id, AgentState::wealth));
    }
}
