# SPEC-009 — Wealth Distribution Metrics

## Status

Draft

## Purpose

Introduce quantitative metrics for analyzing the wealth distribution produced by a simulation run.

The system must calculate wealth-distribution statistics from a completed population snapshot without modifying simulation state.

This specification introduces:

- total wealth;
- mean wealth;
- median wealth;
- minimum wealth;
- maximum wealth;
- Gini coefficient;
- top 1% wealth share;
- top 10% wealth share;
- bottom 50% wealth share.

Metrics must be deterministic and independent of Apache Pekko.

---

# Background

SPEC-004 and SPEC-008 produce a final population state.

A final population by itself is difficult to interpret.

For example:

```text
Agent-1 = 14.25
Agent-2 = 351.40
Agent-3 = 2.11
...
```

SPEC-009 converts that population into interpretable statistics.

Conceptually:

```text
Final Population
      ↓
WealthMetricsCalculator
      ↓
WealthDistributionMetrics
```

These metrics allow different economic configurations and models to be compared objectively.

---

# Core Principle

Metrics are derived data.

They must not alter:

```text
agent wealth
agent identity
population membership
simulation result
```

The calculation is read-only.

---

# Input

Metrics must operate on a completed population snapshot.

Conceptually:

```text
Population
or
List<AgentState>
```

Each entry must provide:

```text
AgentId
Wealth
```

The metric calculator must not query live actor state directly.

For actor-based simulations:

```text
AgentActors
     ↓
final immutable snapshot
     ↓
metrics calculation
```

---

# Acceptance Criteria

## AC-001 — Total wealth

The metrics must include total population wealth.

Given:

```text
10
20
30
40
```

total wealth is:

```text
100
```

---

## AC-002 — Mean wealth

Mean wealth is:

```text
total wealth / population size
```

Given:

```text
10
20
30
40
```

the mean is:

```text
25
```

---

## AC-003 — Median wealth for odd population

Given sorted wealth:

```text
10
20
30
40
50
```

median wealth is:

```text
30
```

---

## AC-004 — Median wealth for even population

Given sorted wealth:

```text
10
20
30
40
```

median wealth is:

```text
25
```

The median is the arithmetic mean of the two central values.

---

## AC-005 — Minimum wealth

The metrics must report the minimum wealth value in the population.

For:

```text
10
20
30
40
```

minimum wealth is:

```text
10
```

---

## AC-006 — Maximum wealth

The metrics must report the maximum wealth value in the population.

For:

```text
10
20
30
40
```

maximum wealth is:

```text
40
```

---

# Gini Coefficient

The Gini coefficient measures inequality.

The result must satisfy:

```text
0 <= Gini <= 1
```

Conceptually:

```text
0
```

represents perfect equality.

A value approaching:

```text
1
```

represents extreme inequality.

---

## AC-007 — Equal population has Gini zero

Given:

```text
100
100
100
100
```

the Gini coefficient must be:

```text
0
```

subject only to the defined decimal precision.

---

## AC-008 — Gini is deterministic

Given the same population state,

the calculator must always produce the same Gini coefficient.

---

## AC-009 — Agent ordering does not affect Gini

These populations:

```text
10, 20, 30, 40
```

and:

```text
40, 10, 30, 20
```

must produce the same Gini coefficient.

---

## AC-010 — Gini result remains within valid range

For every valid non-negative wealth distribution:

```text
0 <= Gini <= 1
```

---

# Gini Formula

The implementation must use a mathematically equivalent standard Gini formulation.

A suitable sorted-value formulation is:

```text
G =
(2 × Σ(i × xi))
-----------------  -  (n + 1) / n
   n × Σxi
```

where:

```text
xi = wealth values sorted ascending
i  = 1-based position
n  = population size
```

Equivalent mathematically correct implementations are acceptable.

The formula must be tested against known examples.

---

# Zero Total Wealth

A population may legally contain agents whose wealth is all zero.

For example:

```text
0
0
0
0
```

The Gini coefficient must be defined as:

```text
0
```

for this case.

This avoids division by zero and represents no wealth inequality among the agents.

---

# Wealth Shares

The metrics must report how much of total wealth is owned by specified population segments.

At minimum:

```text
Top 1%
Top 10%
Bottom 50%
```

Results should be represented as proportions or percentages using one consistent convention.

Prefer a proportion:

```text
0.25
```

to mean:

```text
25%
```

and document that convention.

---

# Population Segment Ordering

Wealth shares must be calculated after sorting agents by wealth.

For top segments:

```text
highest wealth first
```

For bottom segments:

```text
lowest wealth first
```

Agent identity does not affect ranking.

---

## AC-011 — Top 10% wealth share

The metric must report the proportion of total wealth owned by the wealthiest 10% of the population.

Conceptually:

```text
wealth of richest 10%
-----------------------
total population wealth
```

---

## AC-012 — Top 1% wealth share

The metric must report the proportion of total wealth owned by the wealthiest 1%.

---

## AC-013 — Bottom 50% wealth share

The metric must report the proportion of total wealth owned by the poorest 50%.

---

# Segment Size Rounding

Percentile group sizes may not map cleanly to whole agents.

For example:

```text
population size = 15
top 10% = 1.5 agents
```

The calculation must use one explicit rounding rule.

For this specification, use:

```text
segment size = ceil(population size × percentage)
```

with a minimum of one agent for non-zero top-percent metrics.

Examples:

```text
15 × 10% = 1.5
ceil = 2 agents
```

```text
50 × 1% = 0.5
ceil = 1 agent
```

This policy must be centralized and consistently applied.

---

## AC-014 — Small population top 1% remains meaningful

Given:

```text
population size = 10
```

top 1% represents:

```text
1 agent
```

under the defined rounding rule.

---

# Wealth Share Example

Given:

```text
Agent A = 10
Agent B = 20
Agent C = 30
Agent D = 40
Agent E = 100
```

total wealth is:

```text
200
```

For a population of five:

```text
Top 10%
ceil(5 × 0.10)
= 1 agent
```

The richest agent owns:

```text
100 / 200
= 0.50
```

Therefore:

```text
Top 10% wealth share = 0.50
```

---

# Precision

Metrics must use decimal arithmetic where the result depends on wealth values.

Do not use binary floating-point arithmetic as the authoritative calculation representation.

Avoid:

```java
double
float
```

for domain metric calculation.

Prefer:

```java
BigDecimal
```

or existing domain numeric abstractions.

---

# Metrics Precision Policy

Derived ratios require division.

Use one centralized policy.

For example:

```text
calculation scale = 10 decimal places
rounding mode     = HALF_UP
```

Presentation formatting may later use fewer decimal places.

The calculation precision and display precision must not be conflated.

---

# Immutable Result

The metric result should be immutable.

Conceptually:

```text
WealthDistributionMetrics
├── populationSize
├── totalWealth
├── meanWealth
├── medianWealth
├── minimumWealth
├── maximumWealth
├── giniCoefficient
├── top1PercentShare
├── top10PercentShare
└── bottom50PercentShare
```

A Java record is appropriate if it fits the existing design.

---

# Suggested Architecture

Conceptually:

```text
application/domain snapshot
          |
          v
WealthMetricsCalculator
          |
          v
WealthDistributionMetrics
```

The calculator should preferably be a pure function or stateless service.

---

# Domain Independence

SPEC-009 must not depend on Apache Pekko.

The metrics implementation must be usable with both:

```text
SPEC-004 sequential result
```

and:

```text
SPEC-008 actor-based result
```

without knowing which runner created the population.

Do not introduce:

```java
ActorRef
ActorSystem
Behavior
ActorContext
```

into metric calculation.

---

# Suggested Tests

## Scenario 1 — Equal distribution

```text
Given:
100
100
100
100

Then:
population size = 4
total = 400
mean = 100
median = 100
minimum = 100
maximum = 100
Gini = 0
```

---

## Scenario 2 — Unequal distribution

```text
Given:
0
0
0
100

Then:
total = 100
mean = 25
median = 0
minimum = 0
maximum = 100
```

The Gini coefficient should be checked against the mathematically expected value.

---

## Scenario 3 — Odd population median

```text
Given:
10
20
30
40
50

Then:
median = 30
```

---

## Scenario 4 — Even population median

```text
Given:
10
20
30
40

Then:
median = 25
```

---

## Scenario 5 — Input ordering does not matter

```text
Given population A:
10, 20, 30, 40

And population B:
40, 20, 10, 30

Then:
all distribution metrics are identical
```

---

## Scenario 6 — All wealth zero

```text
Given:
0
0
0
0

Then:
total wealth = 0
mean wealth = 0
median wealth = 0
Gini = 0
top shares = 0
bottom share = 0
```

---

## Scenario 7 — Top 10% share

Given a known distribution, verify the richest segment's wealth share using the specified ceiling rule.

---

## Scenario 8 — Top 1% on small population

```text
Given:
population size = 10

Then:
top 1% contains exactly 1 agent
```

---

## Scenario 9 — Bottom 50%

Given an even-sized known population, verify the wealth owned by the lowest half.

---

## Scenario 10 — Metrics do not mutate input

```text
Given:
a final population snapshot

When:
metrics are calculated

Then:
all AgentIds remain unchanged
And:
all wealth values remain unchanged
And:
input ordering is not mutated if the provided collection promises stable ordering
```

Prefer sorting a copy rather than mutating caller-owned collections.

---

# Simulation Integration

After a successful simulation:

```text
SimulationResult
      |
      v
WealthMetricsCalculator
      |
      v
WealthDistributionMetrics
```

The same calculator must work for:

```text
SequentialSimulationRunner
ActorSimulationRunner
```

---

# Optional Simulation Result Integration

The simulation result may expose metrics directly if doing so remains clean.

For example:

```text
SimulationResult
├── configuration
├── finalPopulation
├── executedTradeCount
└── metrics
```

However, the runner should not duplicate the calculation logic.

Metrics must still come from one centralized calculator.

---

# Before-and-After Comparison

The implementation should make it possible to compare initial and final metrics.

Example:

```text
Initial:
Gini = 0.0000
Top 10% = 0.1000

Final:
Gini = 0.6234
Top 10% = 0.5812
```

The comparison itself does not require a new domain model in this specification.

---

# Performance

Metrics calculation should be suitable for reasonably large populations.

Sorting will generally make the complexity approximately:

```text
O(n log n)
```

which is acceptable for the initial implementation.

Do not introduce premature parallel-stream or actor-based metric calculation.

Correctness and reproducibility are more important.

---

# Logging

Metrics should not print directly to:

```java
System.out
```

from domain/application calculation code.

Formatting and presentation belong to an outer application layer.

---

# Error Handling

Metrics require a valid population snapshot.

An empty population should be rejected unless an earlier specification explicitly permits it.

SPEC-001 requires:

```text
population size > 0
```

therefore an empty population supplied to the metrics calculator represents invalid input.

---

## AC-015 — Empty population is rejected

Given:

```text
population = empty
```

when metrics are requested,

then the operation must fail explicitly.

---

# Relationship to Previous Specifications

```text
SPEC-001
Population
     ↓
SPEC-002
Yard-Sale Trade
     ↓
SPEC-003
Seeded Selection
     ↓
SPEC-004
Sequential Simulation
     ↓
SPEC-005
AgentActor
     ↓
SPEC-006
Trade Coordinator
     ↓
SPEC-007
Trade Reservation
     ↓
SPEC-008
Actor-Based Simulation
     ↓
SPEC-009
Wealth Distribution Metrics
```

SPEC-009 measures the outcomes generated by either simulation implementation.

---

# Implementation Constraints

Implement only the metrics required by this specification.

Do not introduce:

- charts;
- graphical dashboards;
- CSV export;
- database persistence;
- REST APIs;
- time-series metrics;
- per-trade metric collection;
- tax models;
- redistribution;
- income;
- investment returns;
- parallel metrics calculation;
- Pekko actors for statistics.

YAGNI applies.

---

# Verification

SPEC-009 is complete when:

1. population size is calculated;
2. total wealth is calculated;
3. mean wealth is calculated;
4. median wealth is calculated;
5. minimum and maximum wealth are calculated;
6. Gini coefficient is calculated correctly;
7. equal wealth produces Gini zero;
8. zero-total-wealth population is handled safely;
9. top 1% share is calculated;
10. top 10% share is calculated;
11. bottom 50% share is calculated;
12. percentile segment rounding follows the specified rule;
13. calculations are deterministic;
14. input data is not mutated;
15. metrics work with both sequential and actor-based simulation results;
16. domain/application metric code has no Pekko dependency;
17. all tests pass;
18. `mvn clean verify` succeeds.

---

# Out of Scope

The following remain deferred:

```text
wealth histogram
Lorenz curve
percentile tables
time-series metrics during simulation
CSV / JSON export
visualization
parallel trade execution
taxation
redistribution
income
investment returns
inheritance
Pekko Cluster
persistence
```

A later specification may introduce controlled parallel trade execution or richer analytical reporting.