# SPEC-004 — Repeatable Simulation Run

## Status

Draft

## Purpose

Combine the capabilities introduced in the previous specifications into a complete sequential simulation run.

A simulation run must:

- initialize a population;
- use seeded random trade selection;
- execute a fixed number of Yard-Sale trades;
- preserve domain invariants throughout the run;
- produce the same final result when executed again with the same configuration and random seed.

This specification defines the first end-to-end wealth simulation.

Apache Pekko is intentionally not required yet.

---

## Background

The previous specifications establish:

```text
SPEC-001
Population initialization

SPEC-002
Single Yard-Sale trade

SPEC-003
Seeded random participant and winner selection
```

SPEC-004 composes them:

```text
Simulation Configuration
        ↓
Initialize Population
        ↓
Seeded Trade Selection
        ↓
Execute Yard-Sale Trade
        ↓
Repeat N times
        ↓
Simulation Result
```

The objective is to create a deterministic reference implementation before introducing concurrent actor execution.

---

# Simulation Configuration

A simulation run requires at least:

```text
population size
initial wealth per agent
stake percentage
number of trades
random seed
```

Example:

```text
population size      = 1,000
initial wealth       = 100.00
stake percentage     = 10%
number of trades     = 1,000,000
random seed          = 12345
```

The initial total wealth is:

```text
1,000 × 100.00
=
100,000.00
```

---

# Acceptance Criteria

## AC-001 — Simulation initializes the population

Given:

```text
population size = N
initial wealth = W
```

when a simulation begins,

then the population must be initialized according to SPEC-001.

Exactly `N` agents must exist.

Each agent must initially have wealth `W`.

---

## AC-002 — Simulation executes the requested number of trades

Given:

```text
number of trades = T
```

when the simulation completes,

then exactly `T` Yard-Sale trade attempts must have been executed.

For example:

```text
number of trades = 10,000
```

must result in:

```text
completed trade steps = 10,000
```

A zero-stake trade still counts as an executed trade step.

---

## AC-003 — Every trade uses seeded selection

Each trade must obtain:

```text
two distinct participants
one winner
```

using the deterministic selection mechanism defined in SPEC-003.

The simulation must not introduce any additional uncontrolled source of randomness.

---

## AC-004 — Every trade follows the Yard-Sale rule

Each executed trade must follow SPEC-002.

For every trade:

```text
stake =
stake percentage
×
wealth of poorer participant
```

The winner gains exactly the stake.

The loser loses exactly the same stake.

---

## AC-005 — Total wealth is conserved

The simulation must not create or destroy wealth.

Therefore:

```text
total wealth before simulation
=
total wealth after simulation
```

For example:

```text
population size = 1,000
initial wealth = 100.00
```

must always result in:

```text
final total wealth = 100,000.00
```

regardless of how wealth becomes distributed.

---

## AC-006 — No agent has negative wealth

At every point during the simulation:

```text
agent wealth >= 0
```

No valid sequence of Yard-Sale trades may result in negative wealth.

---

## AC-007 — Same configuration and seed reproduce the same result

Given two independent simulation runs with identical:

```text
population size
initial wealth
stake percentage
number of trades
random seed
```

the final agent wealth state must be identical.

For example:

```text
Run 1:
seed = 12345

Run 2:
seed = 12345
```

must result in the same:

```text
Agent 1 wealth
Agent 2 wealth
Agent 3 wealth
...
Agent N wealth
```

The final population state must therefore be reproducible.

---

## AC-008 — Different seeds may produce different outcomes

Given identical simulation parameters except for the random seed,

the runs are allowed to produce different wealth distributions.

For example:

```text
Run A seed = 12345
Run B seed = 67890
```

are not required to produce the same final state.

Do not write brittle tests that require two different seeds to always produce different results for very small simulations.

---

## AC-009 — Zero trades is valid

A simulation with:

```text
number of trades = 0
```

is valid.

The final population must be identical to the initial population.

For example:

```text
100 agents
100.00 each
0 trades
```

must result in:

```text
100 agents
100.00 each
total wealth = 10,000.00
```

---

## AC-010 — Negative trade count is invalid

A simulation configuration must reject:

```text
number of trades < 0
```

For example:

```text
number of trades = -1
```

must be rejected.

---

## AC-011 — Simulation result exposes final state

After completion, the caller must be able to inspect the simulation result.

At minimum, the result must expose:

```text
number of executed trades
final population state
total final wealth
```

The exact Java representation is an implementation decision.

---

# Domain Invariants

The following invariants must hold for the complete simulation.

```text
population size remains constant
```

```text
agent identities remain unchanged
```

```text
agent wealth >= 0
```

```text
total wealth remains constant
```

```text
executed trades <= configured trades
```

After normal completion:

```text
executed trades = configured trades
```

---

# Sequential Execution

SPEC-004 must use a sequential execution model.

Conceptually:

```text
for tradeNumber = 1 to configuredTrades:

    selection = tradeSelector.next(population)

    result = yardSaleTrade.execute(
        selection.left,
        selection.right,
        stakePercentage,
        selection.winner
    )

    update population
```

Only one trade is applied at a time.

There must be no concurrent modification of agent wealth in this specification.

---

# Why Sequential First

The sequential simulation acts as the reference model for later Pekko-based execution.

The intended progression is:

```text
Pure domain model
       ↓
Sequential simulation
       ↓
Known deterministic result
       ↓
Pekko actor implementation
       ↓
Compare actor behavior against reference model
```

This makes it possible to distinguish:

```text
economic-model errors
```

from:

```text
concurrency / actor-messaging errors
```

when Pekko is introduced later.

---

# Suggested Domain/Application Concepts

The exact implementation is not prescribed, but this specification may naturally introduce concepts such as:

```text
SimulationConfiguration
SimulationRunner
SimulationResult
```

Existing concepts should be reused:

```text
Population
TradeSelector
TradeSelection
Trade
TradeResult
StakePercentage
```

Do not duplicate rules already implemented by previous specifications.

For example, `SimulationRunner` should not recalculate Yard-Sale stakes itself.

It should delegate to the domain behavior introduced by SPEC-002.

---

# Suggested Flow

Conceptually:

```text
SimulationConfiguration
        |
        v
SimulationRunner
        |
        +--> Population initialization
        |
        +--> Seeded TradeSelector
        |
        +--> YardSaleTrade
        |
        +--> repeat T times
        |
        v
SimulationResult
```

Responsibilities should remain separated.

---

# Suggested Tests

## Scenario 1 — Execute a small simulation

```text
Given:
population size = 10
initial wealth = 100.00
stake percentage = 10%
trade count = 100
seed = 12345

When:
the simulation runs

Then:
100 trades are executed
And:
population size remains 10
And:
total wealth remains 1,000.00
And:
no agent has negative wealth
```

---

## Scenario 2 — Same seed produces identical final population

```text
Given:
configuration C
seed = 12345

When:
simulation C is executed twice independently

Then:
both final populations are identical
```

Comparison should include wealth for each agent identity.

---

## Scenario 3 — Zero trades

```text
Given:
population size = 100
initial wealth = 100.00
trade count = 0

When:
the simulation runs

Then:
all agents still have 100.00
And:
total wealth = 10,000.00
And:
executed trades = 0
```

---

## Scenario 4 — Large number of trades preserves wealth

```text
Given:
population size = 1,000
initial wealth = 100.00
stake percentage = 10%
trade count = 1,000,000
seed = 12345

When:
the simulation runs

Then:
total wealth = 100,000.00
And:
no agent has negative wealth
```

This test may be categorized separately if execution time becomes unsuitable for the normal unit-test suite.

---

## Scenario 5 — Invalid negative trade count

```text
Given:
trade count = -1

When:
simulation configuration is created

Then:
the configuration is rejected
```

---

## Scenario 6 — Population identity remains stable

```text
Given:
a population initialized with N agents

When:
many trades execute

Then:
the same N agent IDs exist after the simulation
And:
no new agent has been introduced
And:
no existing agent has disappeared
```

---

# Reproducibility

Reproducibility is a core property of the simulator.

A simulation run should be describable completely by its configuration.

For example:

```text
populationSize = 1000
initialWealth = 100.00
stakePercentage = 0.10
tradeCount = 1000000
seed = 12345
```

should be sufficient to reproduce the same run.

The seed should therefore be part of the simulation configuration and observable from the result where practical.

---

# Simulation Result

The result should contain enough information to inspect and reproduce the run.

Conceptually:

```text
SimulationResult
├── configuration
├── executed trade count
├── final population
└── total wealth
```

Additional values may be included if naturally required by implementation.

Do not introduce advanced statistics in this specification.

---

# Performance

SPEC-004 should remain correctness-focused.

The implementation should be reasonably efficient for simulations such as:

```text
1,000 agents
1,000,000 trades
```

but optimization must not compromise clarity or deterministic behavior.

Do not introduce concurrency solely for performance.

Performance benchmarking belongs in a later specification.

---

# Domain Independence

The economic domain remains independent of Apache Pekko.

No domain class should depend on:

```java
ActorRef
Behavior
ActorSystem
ActorContext
```

The sequential simulation must run entirely without starting a Pekko actor system.

---

# Error Handling

Invalid configuration must fail before the simulation begins where practical.

Examples include:

```text
population size <= 0
initial wealth < 0
stake percentage <= 0
stake percentage > 100%
trade count < 0
```

Do not partially execute a simulation with an invalid configuration.

---

# Implementation Constraints

Implement only what is necessary to execute the deterministic sequential simulation.

Do not introduce:

- Pekko agent actors;
- actor messaging;
- concurrent trades;
- actor supervision;
- cluster sharding;
- Pekko Cluster;
- Pekko Persistence;
- databases;
- REST APIs;
- metrics dashboards;
- taxation;
- redistribution;
- income;
- inheritance;
- investment returns.

YAGNI applies.

---

# Verification

SPEC-004 is complete when:

1. a population can be initialized;
2. seeded random trade selections are generated;
3. the requested number of Yard-Sale trades executes;
4. total wealth remains unchanged;
5. no agent has negative wealth;
6. population membership remains unchanged;
7. identical configurations and seeds produce identical final states;
8. zero-trade simulations work correctly;
9. invalid configurations are rejected;
10. all tests pass;
11. `mvn clean verify` succeeds;
12. the complete simulation can execute without starting Apache Pekko.

---

# Out of Scope

The following remain deferred:

```text
Pekko PersonActor
actor-owned wealth
actor messaging
concurrent trade execution
trade coordination actors
actor supervision
metrics collection
Gini coefficient
percentile statistics
wealth histograms
taxation
redistribution
income
investment returns
persistence
distributed simulation
```

The next specification may introduce the first Apache Pekko representation while preserving the deterministic sequential simulation as the reference implementation.