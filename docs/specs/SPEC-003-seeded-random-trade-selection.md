# SPEC-003 — Seeded Random Trade Selection

## Status

Draft

## Purpose

Introduce deterministic random selection for Yard-Sale trades.

The system must be able to:

- randomly select two distinct agents from a population;
- randomly select one of those two agents as the winner;
- reproduce the exact same sequence of selections when given the same random seed.

This specification introduces randomness only.

It does not introduce repeated simulation rounds or Apache Pekko actor orchestration.

---

## Background

SPEC-002 defines a deterministic Yard-Sale trade when:

- two participants are already known;
- the winner is already known.

SPEC-003 introduces the mechanism that determines those inputs.

The important requirement is reproducibility.

Given:

```text id="tvs4yd"
same population
same random seed
same number of selections
```

the system must produce:

```text id="v7keql"
the same participant pairs
and the same winners
in the same order
```

This allows simulations to be replayed and test failures to be reproduced.

---

# Acceptance Criteria

## AC-001 — Select exactly two agents

Each trade selection must produce exactly two participants.

---

## AC-002 — Participants must be distinct

The two selected agents must not have the same identity.

For every selection:

```text id="1m07h0"
left agent != right agent
```

An agent must never be selected to trade with itself.

---

## AC-003 — Participants must belong to the population

Both selected agents must exist in the supplied population.

The selector must not create or reference agents outside that population.

---

## AC-004 — Winner must be one of the selected participants

The winner must be either:

```text id="kkgrq1"
left participant
```

or:

```text id="se1qpb"
right participant
```

No third agent may be selected as the winner.

---

## AC-005 — Same seed produces the same sequence

Given the same:

```text id="vgw830"
population
random seed
number of selections
```

two independent runs must produce the same sequence.

For example:

```text id="jhk3uq"
seed = 12345
population = [A, B, C, D]
```

Run 1 and Run 2 must produce identical:

```text id="8fq1oa"
participant pair
winner
participant pair
winner
...
```

---

## AC-006 — Random source must be explicitly seeded

The simulation selection mechanism must not rely on uncontrolled global randomness.

Do not use random sources such as:

```java id="81m9ws"
ThreadLocalRandom.current()
```

or:

```java id="b058ye"
Math.random()
```

inside the domain/application selection logic.

The random source must be supplied or created from an explicit seed.

---

## AC-007 — Population must contain at least two agents

Random trade selection requires at least two distinct agents.

A population containing fewer than two agents must be rejected for trade selection.

For example:

```text id="8bc10s"
population size = 1
```

cannot produce a valid participant pair.

---

## AC-008 — Selection must not modify agent wealth

Selecting participants and a winner must not change any economic state.

The following values must remain unchanged:

```text id="oobke3"
agent wealth
population size
agent identity
```

Selection determines only who participates and who wins.

---

## AC-009 — Selection result is explicit

The result of random selection must make the selected participants and winner observable.

Conceptually:

```text id="b5kpar"
TradeSelection
├── left participant
├── right participant
└── winner
```

The exact Java type is an implementation decision.

---

# Determinism

Randomness in the simulation must be pseudo-random and reproducible.

Conceptually:

```text id="qkv5ow"
seed
  ↓
random source
  ↓
selection 1
selection 2
selection 3
...
```

For example:

```text id="qdgok3"
seed = 12345
```

may generate a sequence such as:

```text id="4g1d6r"
A vs D → D wins
B vs C → B wins
A vs C → C wins
...
```

Running the same configuration again must produce exactly the same sequence.

A different seed is allowed to produce a different sequence.

---

# Suggested Domain/Application Concepts

The implementation may naturally introduce concepts such as:

```text id="zwb1tf"
RandomSource
TradeSelector
TradeSelection
```

The exact class names are not mandated.

The important separation is:

```text id="ax9fzt"
random selection
       ↓
TradeSelection
       ↓
Yard-Sale domain rule from SPEC-002
```

The trade rule itself must remain unaware of how the participants or winner were selected.

---

# Random Source Abstraction

Prefer an abstraction that allows deterministic tests.

For example, conceptually:

```java id="7v2bcw"
public interface RandomSource {

    int nextInt(int bound);

    boolean nextBoolean();
}
```

A production implementation may wrap:

```java id="rkgjyj"
java.util.Random
```

or another suitable seeded pseudo-random generator.

Tests should not depend unnecessarily on implementation-specific random algorithms unless the algorithm itself becomes part of the specification.

---

# Suggested Tests

## Scenario 1 — Two distinct participants are selected

```text id="cv6eb1"
Given:
a population containing 100 agents

When:
a trade selection is requested

Then:
two agents are selected
And:
their IDs are different
```

---

## Scenario 2 — Winner is one of the participants

```text id="k2fqvb"
Given:
two randomly selected participants A and B

When:
the winner is selected

Then:
winner = A
or:
winner = B
```

---

## Scenario 3 — Same seed reproduces the same sequence

```text id="ts9qt5"
Given:
population size = 100
seed = 12345

When:
100 trade selections are generated

And:
another selector is created with seed = 12345

And:
another 100 trade selections are generated

Then:
both sequences are identical
```

---

## Scenario 4 — Different seeds

```text id="mx989h"
Given:
the same population

And:
selector 1 uses seed = 12345

And:
selector 2 uses seed = 67890

When:
multiple selections are generated

Then:
the selectors are not required to produce the same sequence
```

Do not write a brittle test requiring every different seed to generate a different first selection.

---

## Scenario 5 — Single-agent population

```text id="iu99v4"
Given:
population size = 1

When:
trade selection is attempted

Then:
the operation is rejected
```

---

## Scenario 6 — Selection does not modify wealth

```text id="b5r148"
Given:
a valid population

When:
a trade selection is generated

Then:
all agent wealth values remain unchanged
```

---

# Statistical Behavior

SPEC-003 does not require formal statistical fairness testing.

Do not create tests such as:

```text id="anbu9b"
every agent must be selected exactly 10% of the time
```

Random sampling does not guarantee exact short-run equality.

The selector should use an unbiased selection mechanism, but tests should focus on invariants and reproducibility rather than fragile statistical expectations.

Statistical analysis may be introduced in a later specification if required.

---

# Domain Independence

Apache Pekko is not required by this specification.

Random selection must be testable using ordinary Java and JUnit.

Do not introduce:

```java id="cwb2vx"
ActorRef
ActorSystem
Behavior
ActorContext
```

into the economic domain model.

---

# Relationship to SPEC-002

The intended flow becomes:

```text id="ynbh7d"
Population
    ↓
TradeSelector
    ↓
TradeSelection
    ├── Agent A
    ├── Agent B
    └── Winner
             ↓
       Yard-Sale Trade
          SPEC-002
```

SPEC-003 decides:

```text id="2bzuws"
WHO trades
WHO wins
```

SPEC-002 decides:

```text id="4wgxip"
HOW MUCH is transferred
HOW wealth changes
```

These responsibilities must remain separate.

---

# Implementation Constraints

Implement only the behavior required by this specification.

Do not introduce:

- simulation loops;
- simulation rounds;
- concurrent trades;
- Pekko actors for agents;
- actor supervision;
- actor messaging;
- metrics;
- Gini coefficient;
- taxation;
- redistribution;
- persistence;
- clustering.

Do not move economic trade rules into the selector.

YAGNI applies.

---

# Verification

SPEC-003 is complete when:

1. two distinct participants can be selected from a population;
2. the winner is always one of those participants;
3. identical seeds reproduce identical selection sequences;
4. populations smaller than two are rejected;
5. selection does not mutate economic state;
6. all relevant tests pass;
7. `mvn clean verify` succeeds;
8. no Pekko dependency has been introduced into the domain behavior.

---

# Out of Scope

The following are intentionally deferred:

```text id="es2w68"
multiple trade execution
simulation loops
trade counters
simulation termination rules
Pekko agent actors
actor messaging
concurrent execution
metrics collection
wealth histograms
Gini coefficient
taxation
redistribution
```

A later specification will combine population, seeded selection, and Yard-Sale trading into a repeatable simulation run.