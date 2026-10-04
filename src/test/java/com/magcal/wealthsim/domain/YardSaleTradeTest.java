package com.magcal.wealthsim.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class YardSaleTradeTest {
    private final YardSaleTrade trade = new YardSaleTrade();
    private final AgentId leftId = new AgentId("A");
    private final AgentId rightId = new AgentId("B");

    @Test
    void transfersRoundedStakeWhenRicherAgentWins() {
        TradeResult result = trade.execute(
                agent(leftId, "100"), agent(rightId, "33.33"),
                StakePercentage.ofPercent("10"), leftId);

        assertEquals(Money.of("3.33"), result.decision().stake());
        assertEquals(Money.of("103.33"), result.left().wealth());
        assertEquals(Money.of("30.00"), result.right().wealth());
        assertEquals(Money.of("133.33"), result.left().wealth().add(result.right().wealth()));
    }

    @Test
    void poorerAgentCanWinAndOneHundredPercentStakeCannotGoNegative() {
        TradeResult result = trade.execute(
                agent(leftId, "100"), agent(rightId, "50"),
                StakePercentage.ofPercent("100"), rightId);

        assertEquals(Money.of("50"), result.decision().stake());
        assertEquals(Money.of("50"), result.left().wealth());
        assertEquals(Money.of("100"), result.right().wealth());
    }

    @Test
    void zeroWealthProducesZeroStake() {
        TradeResult result = trade.execute(
                agent(leftId, "100"), agent(rightId, "0"),
                StakePercentage.ofPercent("10"), leftId);

        assertEquals(Money.ZERO, result.decision().stake());
        assertEquals(Money.of("100"), result.left().wealth());
        assertEquals(Money.ZERO, result.right().wealth());
    }

    @Test
    void rejectsSelfTradeInvalidWinnerAndInvalidPercentages() {
        AgentState left = agent(leftId, "100");
        assertThrows(IllegalArgumentException.class,
                () -> trade.execute(left, left, StakePercentage.ofPercent("10"), leftId));
        assertThrows(IllegalArgumentException.class,
                () -> trade.execute(left, agent(rightId, "50"),
                        StakePercentage.ofPercent("10"), new AgentId("C")));
        assertThrows(IllegalArgumentException.class, () -> StakePercentage.ofPercent("0"));
        assertThrows(IllegalArgumentException.class, () -> StakePercentage.ofPercent("101"));
    }

    private AgentState agent(AgentId id, String wealth) {
        return new AgentState(id, Money.of(wealth));
    }
}
