package com.magcal.wealthsim.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.magcal.wealthsim.domain.AgentId;
import com.magcal.wealthsim.domain.AgentState;
import com.magcal.wealthsim.domain.Population;
import com.magcal.wealthsim.domain.TradeResult;
import com.magcal.wealthsim.domain.YardSaleTrade;

public final class SequentialSimulationRunner {
    private final YardSaleTrade yardSaleTrade;

    public SequentialSimulationRunner() {
        this(new YardSaleTrade());
    }

    SequentialSimulationRunner(YardSaleTrade yardSaleTrade) {
        this.yardSaleTrade = yardSaleTrade;
    }

    public SimulationResult run(SimulationConfiguration configuration) {
        Population initialPopulation = Population.initialize(
                configuration.populationSize(), configuration.initialWealth());
        Map<AgentId, AgentState> current = new LinkedHashMap<>();
        initialPopulation.agents().forEach(agent -> current.put(agent.id(), agent));
        List<AgentId> identities = List.copyOf(current.keySet());
        TradeSelector selector = new TradeSelector(new SeededRandomSource(configuration.seed()));

        for (int tradeNumber = 0; tradeNumber < configuration.tradeCount(); tradeNumber++) {
            TradeSelection selection = selector.next(identities);
            TradeResult result = yardSaleTrade.execute(
                    current.get(selection.left()),
                    current.get(selection.right()),
                    configuration.stakePercentage(),
                    selection.winner());
            current.put(result.left().id(), result.left());
            current.put(result.right().id(), result.right());
        }

        return new SimulationResult(
                configuration,
                configuration.tradeCount(),
                new Population(List.copyOf(current.values())));
    }
}
