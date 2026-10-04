package com.magcal.wealthsim.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;

import org.junit.jupiter.api.Test;

class PopulationTest {
    @Test
    void initializesEqualPopulationWithUniqueIdentities() {
        Population population = Population.initialize(1_000, Money.of("100"));

        assertEquals(1_000, population.size());
        assertEquals(1_000, new HashSet<>(population.agents().stream().map(AgentState::id).toList()).size());
        assertEquals(Money.of("100000"), population.totalWealth());
        population.agents().forEach(agent -> assertEquals(Money.of("100"), agent.wealth()));
    }

    @Test
    void supportsSingleAgentAndZeroInitialWealth() {
        Population population = Population.initialize(1, Money.ZERO);

        assertEquals(1, population.size());
        assertEquals(Money.ZERO, population.totalWealth());
    }

    @Test
    void rejectsInvalidPopulationInputs() {
        assertThrows(IllegalArgumentException.class, () -> Population.initialize(0, Money.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Population.initialize(-1, Money.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Population.initialize(2, Money.of("-0.01")));
    }
}
