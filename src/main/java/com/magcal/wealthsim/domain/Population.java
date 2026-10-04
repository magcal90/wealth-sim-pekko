package com.magcal.wealthsim.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

public record Population(List<AgentState> agents) {
    public Population {
        Objects.requireNonNull(agents, "agents");
        agents = List.copyOf(agents);
        if (agents.isEmpty()) {
            throw new IllegalArgumentException("Population must contain at least one agent");
        }
        Set<AgentId> identities = new HashSet<>();
        for (AgentState agent : agents) {
            if (!identities.add(agent.id())) {
                throw new IllegalArgumentException("Agent IDs must be unique");
            }
        }
    }

    public static Population initialize(int size, Money initialWealth) {
        if (size <= 0) {
            throw new IllegalArgumentException("Population size must be positive");
        }
        Objects.requireNonNull(initialWealth, "initialWealth");
        if (initialWealth.isNegative()) {
            throw new IllegalArgumentException("Initial wealth must not be negative");
        }
        List<AgentState> agents = IntStream.rangeClosed(1, size)
                .mapToObj(index -> new AgentState(
                        new AgentId("agent-%06d".formatted(index)), initialWealth))
                .toList();
        return new Population(agents);
    }

    public Money totalWealth() {
        return agents.stream()
                .map(AgentState::wealth)
                .reduce(Money.ZERO, Money::add);
    }

    public int size() {
        return agents.size();
    }
}
