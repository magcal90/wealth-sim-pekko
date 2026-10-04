# SPEC-008 — Actor-Based Simulation Run

## Status

Draft

## Purpose

Execute a complete wealth simulation using Apache Pekko actors.

The actor-based simulation must reuse the economic behavior already defined in the earlier specifications while coordinating repeated trades through actor messaging.

This specification introduces:

- actor-based population startup;
- seeded participant and winner selection;
- repeated trade scheduling;
- trade reservation;
- one coordinator per trade;
- completion tracking;
- final population state collection;
- deterministic verification against the sequential reference simulation.

The goal is not maximum parallelism yet.

The goal is to prove that the actor-based implementation produces correct, reproducible results.

---

# Background

The earlier specifications establish:

```text
SPEC-001
Population initialization

SPEC-002
Single Yard-Sale trade

SPEC-003
Seeded random participant and winner selection

SPEC-004
Sequential reference simulation

SPEC-005
AgentActor state ownership

SPEC-006
Actor trade coordination

SPEC-007
Agent trade reservation
```

SPEC-008 combines these capabilities into a complete actor-driven simulation.

Conceptually:

```text
SimulationCoordinator
        |
        +--> AgentActor 1
        +--> AgentActor 2
        +--> AgentActor 3
        +--> ...
        |
        +--> seeded trade selection
        |
        +--> TradeCoordinator T1
        +--> TradeCoordinator T2
        +--> ...
        |
        v
SimulationResult
```

---

# Core Requirement

Given a simulation configuration:

```text
population size
initial wealth
stake percentage
trade count
random seed
```

the actor-based simulation must:

```text
create the population
        ↓
execute the requested trades
        ↓
preserve economic invariants
        ↓
collect the final state
        ↓
return a SimulationResult
```

For the same configuration, the result must be reproducible.

---

# Reference Model

SPEC-004 remains the reference implementation for economic correctness.

The actor-based simulation must be verifiable against it.

Conceptually:

```text
SimulationConfiguration
        |
        +----------------------+
        |                      |
        v                      v
Sequential Runner       Actor-Based Runner
SPEC-004                SPEC-008
        |                      |
        v                      v
SimulationResult       SimulationResult
        |                      |
        +---------- compare ---+
```

For deterministic execution defined by this specification, equivalent configurations must produce equivalent final population state.

---

# Simulation Configuration

SPEC-008 must reuse the existing simulation configuration where practical.

At minimum:

```text
populationSize
initialWealth
stakePercentage
tradeCount
seed
```

Example:

```text
populationSize  = 1,000
initialWealth   = 100.00
stakePercentage = 10%
tradeCount      = 100,000
seed            = 12345
```

Do not create separate incompatible configuration models for the sequential and actor-based implementations without a strong reason.

---

# Actor Topology

A minimal topology may be:

```text
SimulationGuardian
        |
        v
SimulationCoordinator
        |
        +--> AgentActor[1]
        +--> AgentActor[2]
        +--> ...
        +--> AgentActor[N]
        |
        +--> TradeCoordinator
        +--> TradeCoordinator
        +--> ...
```

The exact hierarchy is an implementation decision.

---

# Simulation Coordinator Responsibility

The `SimulationCoordinator` is responsible for orchestration.

It may:

```text
create AgentActors
maintain ActorRefs by AgentId
use seeded trade selection
start trade coordinators
track completed trade steps
collect final agent state
produce SimulationResult
```

It must not:

```text
directly own authoritative agent wealth
duplicate Yard-Sale calculations
modify agent state directly
```

---

# Acceptance Criteria

## AC-001 — Actor population is created

Given:

```text
population size = N
```

when the actor-based simulation starts,

then exactly `N` economic `AgentActor` instances must be created.

Each actor must represent one unique `AgentId`.

---

## AC-002 — Initial actor wealth matches SPEC-001

Given:

```text
initial wealth = W
```

every created `AgentActor` must begin with:

```text
wealth = W
```

The initial total wealth must equal:

```text
N × W
```

---

## AC-003 — Requested number of trade steps is executed

Given:

```text
tradeCount = T
```

the simulation must execute exactly `T` trade steps before normal completion.

A zero-stake trade counts as a completed trade step.

A trade rejected because an agent is temporarily busy does not count as a completed economic trade unless the scheduler explicitly reschedules it and eventually completes that trade step.

The final count must equal the configured trade count.

---

# Deterministic Scheduling

To preserve reproducibility, SPEC-008 must initially use deterministic scheduling semantics.

The simplest reference behavior is:

```text
select one trade
        ↓
execute it to terminal result
        ↓
select next trade
```

rather than launching many trades concurrently.

This means:

```text
trade 1 completes
before
trade 2 begins
```

for the initial actor-based reference implementation.

Pekko still provides actor ownership and messaging, but the simulation scheduler remains sequential at the trade level.

---

## AC-004 — Trade selections use seeded randomness

Trade participants and winners must be selected using the seeded mechanism from SPEC-003.

No uncontrolled random source may be introduced.

Do not use:

```java
Math.random()
ThreadLocalRandom.current()
```

inside the simulation orchestration.

---

## AC-005 — Same seed reproduces same selections

Given identical:

```text
population
trade count
seed
```

the actor-based scheduler must generate the same logical trade-selection sequence.

---

## AC-006 — Trades are initially scheduled one at a time

The initial SPEC-008 implementation must not have multiple active `TradeCoordinator` instances modifying economic state concurrently.

At most one economic trade is active at a time.

This provides deterministic comparison with SPEC-004.

Parallel scheduling belongs in a later specification.

---

# Trade Execution

For each selected trade:

```text
SimulationCoordinator
        |
        v
TradeSelection
        |
        v
spawn TradeCoordinator
        |
        v
reserve both agents
        |
        v
read states
        |
        v
execute Yard-Sale trade
        |
        v
release participants
        |
        v
TradeCompleted
        |
        v
schedule next trade
```

---

## AC-007 — Trade coordination follows SPEC-006

Every actor-based trade must use the coordination protocol defined in SPEC-006.

The scheduler must not bypass the `TradeCoordinator` by directly issuing debit and credit commands.

---

## AC-008 — Reservations follow SPEC-007

Every non-zero actor-based trade must respect the reservation protocol.

Both participants must be successfully reserved before trade state is used.

---

## AC-009 — Scheduler waits for terminal trade result

The next trade must not be scheduled until the current trade has reached a terminal result.

Examples:

```text
TradeCompleted
TradeRejected
CompensatedFailure
CompensationFailed
```

The scheduler must explicitly handle each terminal outcome.

---

# Trade Failure Semantics

Not every coordination failure should terminate the whole simulation.

Failures must be classified.

---

## Recoverable Trade Failure

Examples may include:

```text
PARTICIPANT_BUSY
temporary reservation failure
zero-stake no-op completion
```

For the sequential SPEC-008 scheduler, `PARTICIPANT_BUSY` should normally not occur because only one trade is active.

If it does occur unexpectedly, it should be handled explicitly rather than silently ignored.

---

## Non-Recoverable Invariant Failure

Examples:

```text
COMPENSATION_FAILED
unexpected wealth corruption
missing actor
invalid trade state
```

These indicate that the simulation can no longer guarantee its economic invariants.

---

## AC-010 — Invariant-breaking failure aborts simulation

If a trade reaches a state where wealth conservation cannot be guaranteed, the simulation must fail explicitly.

It must not continue producing a result as though the run were valid.

---

# Completion Counting

The simulation must clearly distinguish:

```text
scheduled trade step
active trade
completed trade step
failed simulation
```

For normal completion:

```text
completedTradeCount = configuredTradeCount
```

---

## AC-011 — Zero-stake trade counts as completed

Given a valid participant pair where:

```text
stake = 0
```

the trade still counts toward the requested number of trade steps.

---

## AC-012 — Failed invariant-breaking trade does not produce successful completion

If the run aborts due to an unrecoverable error:

```text
SimulationFailed
```

must be returned instead of:

```text
SimulationCompleted
```

---

# Final State Collection

After all trade steps complete:

```text
SimulationCoordinator
        |
        +--> GetState Agent 1
        +--> GetState Agent 2
        +--> ...
        +--> GetState Agent N
```

The simulation must collect the final state of every agent.

---

## AC-013 — Final result contains every agent

For population size `N`, the final simulation result must contain exactly `N` agent states.

---

## AC-014 — Agent identity remains stable

The same set of `AgentId` values created at simulation startup must exist in the final result.

No actor-based trade may:

```text
create a new economic agent
remove an economic agent
change an agent's identity
```

---

## AC-015 — Final total wealth is conserved

After successful completion:

```text
sum(final agent wealth)
=
initial total wealth
```

For example:

```text
1,000 agents
100.00 each
```

must finish with:

```text
total wealth = 100,000.00
```

---

## AC-016 — No final agent has negative wealth

For every final agent:

```text
wealth >= 0
```

---

# Deterministic Comparison with SPEC-004

For the deterministic sequential scheduling mode defined by SPEC-008:

```text
SPEC-004 sequential runner
```

and:

```text
SPEC-008 actor runner
```

must be equivalent.

---

## AC-017 — Same configuration produces same final state as SPEC-004

Given identical:

```text
population size
initial wealth
stake percentage
trade count
seed
```

the final wealth associated with each `AgentId` must equal the sequential reference result.

Conceptually:

```text
Sequential:
Agent-1 = 45.27
Agent-2 = 138.91
...

Actor:
Agent-1 = 45.27
Agent-2 = 138.91
...
```

The results must match.

---

# Important Determinism Constraint

Do not depend on:

```text
mailbox timing
thread scheduling
dispatcher timing
actor execution order
```

to determine economic outcomes.

Those are runtime implementation details and may vary between executions.

Economic randomness must come only from the explicitly seeded simulation random source.

This is why SPEC-008 schedules one logical trade at a time.

---

# Zero-Trade Simulation

## AC-018 — Zero trade count is valid

Given:

```text
tradeCount = 0
```

the actor population must still be created.

The final state must equal the initial state.

No `TradeCoordinator` needs to be created.

---

# Suggested Simulation Protocol

The exact message model is not mandated, but it may contain concepts such as:

```text
StartSimulation
TradeFinished
TradeFailed
AgentStateReceived
```

The coordinator may maintain internal runtime state such as:

```text
configuration
agent refs
random selector
completed trade count
current trade selection
collected final states
replyTo
```

---

# Suggested Coordinator States

Conceptually:

```text
INITIALIZING
     |
     v
READY
     |
     v
RUNNING_TRADE
     |
     +---------------------+
     |                     |
more trades            all done
     |                     |
     v                     v
RUNNING_TRADE       COLLECTING_RESULTS
                           |
                           v
                       COMPLETED
```

Failure may transition from any active state to:

```text
FAILED
```

---

# Actor Creation

Agent actor names should be deterministic where practical.

For example:

```text
agent-000001
agent-000002
...
```

Actor path naming must not become part of the economic domain model.

`AgentId` remains the economic identity.

Actor path is runtime identity.

These concepts should not be confused.

---

# Trade Coordinator Lifecycle

Each trade should use a short-lived coordinator.

Conceptually:

```text
SimulationCoordinator
      |
      +--> spawn TradeCoordinator(T1)
                 |
                 v
              complete
                 |
                 v
               stop
```

After receiving the terminal result, the simulation coordinator proceeds to the next trade.

---

# Trade IDs

Trade IDs should be unique within one simulation run.

For deterministic runs, a simple sequence may be appropriate:

```text
trade-1
trade-2
trade-3
...
```

Trade IDs do not need random UUID generation unless there is a real requirement.

Using deterministic IDs improves diagnostics and test reproducibility.

---

# Suggested Tests

Use a combination of ordinary JUnit tests and Pekko Typed Actor TestKit.

---

## Scenario 1 — Small actor simulation

```text
Given:
population size = 10
initial wealth = 100.00
stake percentage = 10%
trade count = 100
seed = 12345

When:
the actor simulation completes

Then:
completed trade count = 100
population size = 10
total wealth = 1,000.00
no agent has negative wealth
```

---

## Scenario 2 — Same actor simulation is reproducible

```text
Given:
configuration C
seed = 12345

When:
actor simulation C is executed twice using independent ActorSystems

Then:
both final population states are identical
```

---

## Scenario 3 — Actor result equals sequential result

```text
Given:
configuration C

When:
SPEC-004 sequential simulation executes

And:
SPEC-008 actor simulation executes

Then:
final population states are identical by AgentId
```

This is one of the most important tests in SPEC-008.

---

## Scenario 4 — Zero trades

```text
Given:
population size = 100
initial wealth = 100.00
trade count = 0

When:
actor simulation runs

Then:
all 100 agents still have 100.00
completed trade count = 0
total wealth = 10,000.00
```

---

## Scenario 5 — Large actor simulation preserves wealth

```text
Given:
population size = 1,000
initial wealth = 100.00
stake percentage = 10%
trade count = 100,000
seed = 12345

When:
the actor simulation completes

Then:
total wealth = 100,000.00
And:
no agent has negative wealth
```

Keep larger performance-oriented cases out of the normal unit suite if they make builds unnecessarily slow.

---

## Scenario 6 — Every agent appears in final result

```text
Given:
population size = 50

When:
simulation completes

Then:
exactly 50 agent states are returned
And:
every original AgentId occurs exactly once
```

---

## Scenario 7 — Coordinator waits for trade completion

Using probes or controlled actors:

```text
Given:
trade T1 has started

And:
T1 has not yet returned a terminal result

Then:
trade T2 must not start
```

---

## Scenario 8 — Invariant-breaking failure aborts simulation

Using a controlled failing trade implementation or actor:

```text
Given:
a trade reports COMPENSATION_FAILED

When:
the simulation coordinator receives the failure

Then:
the simulation stops scheduling new trades
And:
returns SimulationFailed
```

---

## Scenario 9 — Agent actors terminate with simulation

After result collection and successful completion, the actor hierarchy should be capable of terminating cleanly.

Tests should verify that the simulation does not leave unnecessary actors running indefinitely.

---

# Simulation Result

The actor-based simulation should produce a result compatible with the sequential simulation result where practical.

Conceptually:

```text
SimulationResult
├── configuration
├── executedTradeCount
├── finalPopulation
└── totalWealth
```

Avoid creating:

```text
SequentialSimulationResult
ActorSimulationResult
```

with unnecessary duplication if one common domain/application result model is sufficient.

---

# Separation of Concerns

The intended responsibility split is:

```text
Domain
------
wealth
trade rules
stake calculation
invariants

Application
-----------
simulation configuration
simulation result
selection abstraction

Actor Runtime
-------------
AgentActor
TradeCoordinator
SimulationCoordinator
message protocols
timeouts
reservation
state ownership
```

Pekko must not leak downward into the economic domain.

---

# Timeouts

Actor interactions must not wait forever.

Timeouts may apply to:

```text
trade coordination
final state collection
reservation
agent responses
```

A timeout must result in an explicit failure path.

Timeout values should be centrally configurable.

---

# AC-019 — Final state collection cannot wait forever

If one or more agent actors fail to return final state within the configured timeout:

```text
simulation must fail explicitly
```

rather than hang indefinitely.

---

# Logging

The simulation should support useful diagnostic logging.

At minimum, failures should make it possible to identify:

```text
simulation seed
trade number
trade ID
participant IDs
failure reason
```

Do not log every trade at INFO level for large simulations by default.

Per-trade logging should normally be:

```text
DEBUG
```

or disabled.

---

# Performance

SPEC-008 prioritizes correctness and architectural validation.

Because trades are intentionally scheduled sequentially:

```text
maximum throughput
```

is not a goal of this specification.

Do not introduce concurrency merely to improve benchmark numbers.

A later specification may add controlled parallel trade execution.

---

# Actor System Lifecycle

The simulation must support clean ActorSystem shutdown.

After:

```text
SimulationCompleted
```

or:

```text
SimulationFailed
```

the application must be able to terminate without hanging actor resources.

The exact ownership of ActorSystem termination depends on the application entry-point design.

The domain and simulation result must not call ActorSystem APIs directly.

---

# Domain Independence

The domain package must remain free of Pekko dependencies.

Do not import into the domain:

```java
ActorRef
Behavior
ActorSystem
ActorContext
```

The dependency direction remains:

```text
actor
   ↓
application
   ↓
domain
```

where appropriate.

---

# Implementation Constraints

Implement only the deterministic actor-based simulation required by this specification.

Do not introduce:

- parallel trade scheduling;
- multiple simultaneous TradeCoordinators;
- scheduler retry backoff;
- worker pools;
- routers;
- Pekko Cluster;
- cluster sharding;
- Pekko Persistence;
- databases;
- REST APIs;
- taxation;
- redistribution;
- income;
- inheritance;
- investment returns;
- Gini calculation;
- dashboarding.

YAGNI applies.

---

# Verification

SPEC-008 is complete when:

1. a complete actor population can be created;
2. seeded trade selections are used;
3. exactly the configured number of trades completes;
4. at most one economic trade is active at a time;
5. every trade uses the coordinator and reservation protocol;
6. total wealth remains conserved;
7. no agent has negative wealth;
8. final state contains every original agent;
9. identical seeds reproduce identical results;
10. actor results match the SPEC-004 sequential reference result;
11. invariant-breaking failures abort the simulation;
12. final-state collection handles timeouts;
13. the actor hierarchy terminates cleanly;
14. all tests pass;
15. `mvn clean verify` succeeds.

---

# Out of Scope

The following are intentionally deferred:

```text
parallel actor trades
multiple active TradeCoordinators
trade scheduling optimization
conflict retry policy
scheduler fairness
simulation throughput benchmarking
wealth metrics
Gini coefficient
percentile distribution
wealth histograms
taxation
redistribution
income
investment returns
Pekko Persistence
Pekko Cluster
cluster sharding
```

A later specification may introduce wealth-distribution metrics before controlled parallel trade execution.