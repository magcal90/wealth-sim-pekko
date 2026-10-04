package com.magcal.wealthsim;

import java.util.Map;
import java.util.stream.Collectors;

import com.magcal.wealthsim.actor.ActorSimulationRunner;
import com.magcal.wealthsim.application.SequentialSimulationRunner;
import com.magcal.wealthsim.application.SimulationConfiguration;
import com.magcal.wealthsim.application.SimulationResult;
import com.magcal.wealthsim.application.WealthDistributionMetrics;
import com.magcal.wealthsim.application.WealthMetricsCalculator;
import com.magcal.wealthsim.domain.AgentId;
import com.magcal.wealthsim.domain.AgentState;
import com.magcal.wealthsim.domain.Money;
import com.magcal.wealthsim.domain.StakePercentage;

public final class WealthSimApplication {
    private WealthSimApplication() {
    }

    public static void main(String[] args) throws Exception {
        SimulationConfiguration configuration = new SimulationConfiguration(
                1_000,
                Money.of("100"),
                StakePercentage.ofPercent("10"),
                1_000_000,
                12_345L);

        SimulationResult sequential = new SequentialSimulationRunner().run(configuration);
        SimulationResult actor = new ActorSimulationRunner().run(configuration);
        WealthDistributionMetrics metrics = new WealthMetricsCalculator().calculate(actor);
        boolean resultsMatch = wealthById(sequential).equals(wealthById(actor));

        print(configuration, actor, metrics, resultsMatch);
    }

    private static Map<AgentId, Money> wealthById(SimulationResult result) {
        return result.finalPopulation().agents().stream()
                .collect(Collectors.toMap(AgentState::id, AgentState::wealth));
    }

    private static void print(
            SimulationConfiguration configuration,
            SimulationResult result,
            WealthDistributionMetrics metrics,
            boolean resultsMatch) {
        System.out.printf("""
                Configuration
                  Population:       %,d
                  Initial wealth:   %s
                  Stake percentage: %s%%
                  Trade count:      %,d
                  Seed:             %d

                Result
                  Completed trades: %,d
                  Total wealth:     %s
                  Mean:             %s
                  Median:           %s
                  Minimum:          %s
                  Maximum:          %s
                  Gini:             %s
                  Top 1%% share:     %s
                  Top 10%% share:    %s
                  Bottom 50%% share: %s

                Verification
                  Wealth conserved:          %s
                  No negative wealth:        %s
                  Sequential vs actor result: %s
                """,
                configuration.populationSize(),
                configuration.initialWealth(),
                configuration.stakePercentage().proportion().movePointRight(2).toPlainString(),
                configuration.tradeCount(),
                configuration.seed(),
                result.executedTradeCount(),
                metrics.totalWealth(),
                metrics.meanWealth(),
                metrics.medianWealth(),
                metrics.minimumWealth(),
                metrics.maximumWealth(),
                metrics.giniCoefficient().toPlainString(),
                metrics.topOnePercentShare().toPlainString(),
                metrics.topTenPercentShare().toPlainString(),
                metrics.bottomFiftyPercentShare().toPlainString(),
                result.totalWealth().equals(configuration.initialTotalWealth()),
                result.finalPopulation().agents().stream().noneMatch(agent -> agent.wealth().isNegative()),
                resultsMatch);
    }
}
