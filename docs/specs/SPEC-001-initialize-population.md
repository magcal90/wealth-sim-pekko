# SPEC-001 — Initialize Population

## Status

Draft

## Purpose

Introduce the initial economic population for the wealth simulation.

The simulation must be able to create a specified number of economic agents, with each agent starting with the same amount of wealth.

This specification establishes the initial domain state only.

It does **not** introduce trading, taxation, income, redistribution, investment returns, or other economic behavior.

---

## Behavior

Given:

- a population size;
- an initial wealth amount per agent;

the system creates exactly that number of agents and assigns the specified initial wealth to every agent.

Each agent must have a unique identity.

---

## Example

Given:

```text
Population size: 1,000
Initial wealth per agent: 100.00
```

the resulting population must contain:

```text
1,000 agents
```

and every agent must have:

```text
wealth = 100.00
```

The total wealth of the population must therefore be:

```text
1,000 × 100.00 = 100,000.00
```

---

## Acceptance Criteria

### AC-001 — Population size

Given a requested population size of `N`,

when the population is initialized,

then exactly `N` agents must exist.

---

### AC-002 — Initial wealth

Given an initial wealth amount of `W`,

when the population is initialized,

then every agent must have wealth equal to `W`.

---

### AC-003 — Unique agent identity

Every agent in the population must have a unique identifier.

No two agents may have the same identifier.

---

### AC-004 — Total initial wealth

For:

```text
population size = N
initial wealth = W
```

the total population wealth must equal:

```text
N × W
```

For example:

```text
N = 1,000
W = 100.00

total wealth = 100,000.00
```

---

### AC-005 — Wealth cannot be negative

An agent cannot be initialized with negative wealth.

For example:

```text
initial wealth = -10.00
```

must be rejected.

---

### AC-006 — Population size must be positive

A population cannot be created with zero or a negative number of agents.

The following values must be rejected:

```text
0
-1
-100
```

---

## Domain Rules

The following invariants apply:

```text
population size > 0
```

```text
agent wealth >= 0
```

```text
agent IDs are unique
```

```text
total wealth = sum of all agent wealth
```

For an equally initialized population:

```text
total wealth = population size × initial wealth per agent
```

---

## Precision

Wealth represents monetary or resource value and must not use binary floating-point arithmetic for domain calculations.

Do not use:

```java
double
float
```

Use a decimal representation suitable for exact domain calculations, such as:

```java
BigDecimal
```

The domain should encapsulate monetary/resource values rather than spreading raw `BigDecimal` calculations throughout the application.

---

## Domain Independence

The behavior defined in this specification is domain behavior.

The domain implementation must not depend on Apache Pekko.

In particular, domain classes must not contain:

```java
ActorRef
Behavior
ActorSystem
```

or other Pekko types.

The population should be testable without starting an actor system.

---

## Pekko Scope

Apache Pekko integration is **not required** to satisfy this specification.

A later specification may introduce actors representing agents.

SPEC-001 first establishes what an economic agent and its initial wealth mean independently of the runtime model.

This separation allows:

```text
Domain model
    ↓
tested independently
    ↓
Actor representation
    ↓
concurrency / messaging
```

---

## Suggested Tests

The implementation should include tests covering at least the following scenarios.

### Scenario 1 — Create a normal population

```text
Given population size = 1000
And initial wealth = 100.00

When the population is initialized

Then population size = 1000
And every agent has wealth = 100.00
And total wealth = 100000.00
And all agent IDs are unique
```

### Scenario 2 — Single agent population

```text
Given population size = 1
And initial wealth = 100.00

When the population is initialized

Then population size = 1
And total wealth = 100.00
```

### Scenario 3 — Zero initial wealth

Zero wealth is valid.

```text
Given population size = 100
And initial wealth = 0.00

When the population is initialized

Then population size = 100
And every agent has wealth = 0.00
And total wealth = 0.00
```

### Scenario 4 — Negative initial wealth

```text
Given population size = 100
And initial wealth = -1.00

When initialization is attempted

Then initialization is rejected
```

### Scenario 5 — Zero population

```text
Given population size = 0

When initialization is attempted

Then initialization is rejected
```

### Scenario 6 — Negative population

```text
Given population size = -10

When initialization is attempted

Then initialization is rejected
```

---

## Implementation Constraints

Implement only what is required to satisfy this specification.

Do not introduce:

- trading;
- random number generation;
- a market;
- taxation;
- redistribution;
- income;
- investment returns;
- inheritance;
- persistence;
- Pekko Cluster;
- Pekko Persistence;
- distributed agents.

Do not create abstractions for future economic models unless required by this specification.

YAGNI applies.

---

## Verification

The specification is complete when:

1. all acceptance criteria are represented by automated tests;
2. all tests pass;
3. `mvn clean verify` succeeds;
4. the domain implementation has no dependency on Apache Pekko;
5. the implementation contains no economic behavior beyond population initialization.

---

## Out of Scope

The following are intentionally deferred to later specifications:

```text
agent-to-agent trading
random participant selection
trade stake calculation
winner selection
wealth transfer
wealth conservation across trades
simulation iterations
seeded randomness
wealth distribution metrics
Gini coefficient
taxation
redistribution
```

The next specification can build on this initial population without changing the behavior defined here.