# SPEC-002 — Yard-Sale Trade

## Status

Draft

## Purpose

Introduce the first economic interaction in `wealth-sim-pekko`.

Two distinct agents may participate in a single Yard-Sale trade.

A fixed percentage of the poorer agent's wealth is placed at risk. One participant wins the stake and the other loses the same amount.

This specification defines the economic rule only.

Apache Pekko is not required to satisfy this specification.

---

## Background

In the Yard-Sale model, two economic agents are selected to trade.

The amount at risk is based on the wealth of the poorer participant:

```text
stake = stake percentage × min(left wealth, right wealth)
```

One participant wins the stake.

The winner's wealth increases by the stake.

The loser's wealth decreases by the same amount.

Therefore, the total wealth of the two participants must remain unchanged.

---

## Example

Given:

```text
Agent A wealth = 100.00
Agent B wealth = 50.00
Stake percentage = 10%
Winner = Agent A
```

the poorer agent has:

```text
50.00
```

The stake is therefore:

```text
50.00 × 10% = 5.00
```

After the trade:

```text
Agent A = 105.00
Agent B = 45.00
```

Total wealth before:

```text
150.00
```

Total wealth after:

```text
150.00
```

---

# Acceptance Criteria

## AC-001 — Trade requires two distinct agents

A trade must involve exactly two distinct agents.

An agent cannot trade with itself.

For example:

```text
Agent A vs Agent A
```

must be rejected.

---

## AC-002 — Stake is based on the poorer participant

Given:

```text
Agent A wealth = A
Agent B wealth = B
Stake percentage = P
```

the stake must be:

```text
P × min(A, B)
```

The richer participant's wealth must not be used as the basis for calculating the stake.

---

## AC-003 — Winner receives the stake

The selected winner's wealth must increase by exactly the stake amount.

For example:

```text
A = 100.00
B = 50.00
P = 10%
Winner = A
```

results in:

```text
stake = 5.00
A = 105.00
```

---

## AC-004 — Loser loses the stake

The selected loser's wealth must decrease by exactly the same stake amount.

Using the same example:

```text
B = 50.00
stake = 5.00
```

results in:

```text
B = 45.00
```

---

## AC-005 — Wealth is conserved

A Yard-Sale trade must not create or destroy wealth.

Therefore:

```text
wealth before trade
=
wealth after trade
```

For two participants:

```text
A.before + B.before
=
A.after + B.after
```

---

## AC-006 — Wealth cannot become negative

A valid trade must never produce negative wealth.

Because the stake is calculated from the poorer participant's wealth and the stake percentage must not exceed 100%, the loser's resulting wealth must be greater than or equal to zero.

---

## AC-007 — Zero-wealth participant

If either participant has zero wealth:

```text
min(A, B) = 0
```

therefore:

```text
stake = 0
```

The trade is valid but produces no wealth transfer.

For example:

```text
A = 100.00
B = 0.00
P = 10%
```

results in:

```text
stake = 0.00
A = 100.00
B = 0.00
```

---

## AC-008 — Valid stake percentage

The stake percentage must satisfy:

```text
0 < stake percentage <= 100%
```

The following are invalid:

```text
0%
-10%
110%
```

The following are valid:

```text
1%
10%
50%
100%
```

---

## AC-009 — Winner must be one of the participants

The specified winner must be either:

```text
left participant
```

or:

```text
right participant
```

A third unrelated agent cannot be declared the winner of the trade.

---

# Domain Rules

The following invariants apply.

```text
left agent != right agent
```

```text
0 < stake percentage <= 100%
```

```text
stake =
stake percentage × min(left wealth, right wealth)
```

```text
winner wealth after =
winner wealth before + stake
```

```text
loser wealth after =
loser wealth before - stake
```

```text
wealth after >= 0
```

```text
total wealth before =
total wealth after
```

---

# Determinism

Winner selection is intentionally outside this specification.

SPEC-002 receives the winner as an explicit input.

For example:

```text
trade(
    Agent A,
    Agent B,
    stake percentage = 10%,
    winner = A
)
```

The domain trade operation must therefore be completely deterministic.

Random winner selection will be introduced separately.

This allows the economic rule to be tested independently from random-number generation.

---

# Precision and Rounding

Wealth calculations must use decimal arithmetic.

Do not use:

```java
double
float
```

Use the domain representation introduced by SPEC-001.

Stake calculation may produce values requiring rounding.

The simulation must define one consistent monetary precision and rounding policy.

For the initial implementation, use:

```text
scale: 2 decimal places
rounding: HALF_UP
```

Example:

```text
wealth = 33.33
stake percentage = 10%

raw stake = 3.333
stake = 3.33
```

The rounding policy must be centralized rather than duplicated throughout the implementation.

---

# Suggested Domain Model

The exact implementation is not prescribed, but the model may naturally introduce concepts such as:

```text
Trade
TradeResult
StakePercentage
Winner
```

Existing concepts from SPEC-001 should be reused where appropriate:

```text
Agent
AgentId
Wealth / Money
```

Avoid exposing raw `BigDecimal` values throughout the domain if a value object already represents wealth.

---

# Suggested Operation

Conceptually:

```java
TradeResult result = trade.execute(
    leftAgent,
    rightAgent,
    stakePercentage,
    winner
);
```

The result should make the trade outcome observable without requiring Apache Pekko.

For example, it may contain:

```text
left agent after trade
right agent after trade
stake
winner
```

The exact class shape is an implementation decision.

---

# Suggested Tests

## Scenario 1 — Richer agent wins

```text
Given:
A wealth = 100.00
B wealth = 50.00
Stake percentage = 10%
Winner = A

When:
the trade executes

Then:
stake = 5.00
A wealth = 105.00
B wealth = 45.00
total wealth = 150.00
```

---

## Scenario 2 — Poorer agent wins

```text
Given:
A wealth = 100.00
B wealth = 50.00
Stake percentage = 10%
Winner = B

When:
the trade executes

Then:
stake = 5.00
A wealth = 95.00
B wealth = 55.00
total wealth = 150.00
```

---

## Scenario 3 — Equal wealth

```text
Given:
A wealth = 100.00
B wealth = 100.00
Stake percentage = 10%
Winner = A

When:
the trade executes

Then:
stake = 10.00
A wealth = 110.00
B wealth = 90.00
```

---

## Scenario 4 — Zero-wealth participant

```text
Given:
A wealth = 100.00
B wealth = 0.00
Stake percentage = 10%

When:
the trade executes

Then:
stake = 0.00
A wealth = 100.00
B wealth = 0.00
```

---

## Scenario 5 — 100% stake

```text
Given:
A wealth = 100.00
B wealth = 50.00
Stake percentage = 100%
Winner = A

When:
the trade executes

Then:
stake = 50.00
A wealth = 150.00
B wealth = 0.00
```

---

## Scenario 6 — Self trade

```text
Given:
Agent A

When:
a trade between A and A is attempted

Then:
the trade is rejected
```

---

## Scenario 7 — Invalid zero percentage

```text
Given:
Stake percentage = 0%

When:
a trade is created

Then:
the operation is rejected
```

---

## Scenario 8 — Invalid percentage above 100%

```text
Given:
Stake percentage = 110%

When:
a trade is created

Then:
the operation is rejected
```

---

## Scenario 9 — Wealth conservation

```text
Given:
two valid agents
and a valid stake percentage
and either participant as winner

When:
the trade executes

Then:

left wealth before
+ right wealth before

must equal

left wealth after
+ right wealth after
```

This invariant should be tested across multiple representative values.

---

# Domain Independence

This specification defines economic domain behavior.

The implementation must not depend on Apache Pekko.

The domain must not contain:

```java
ActorRef
Behavior
ActorSystem
ActorContext
```

or other Pekko types.

The complete specification must be testable using ordinary JUnit tests.

---

# Mutation and State

Prefer domain operations whose behavior is explicit and easy to test.

The domain should avoid hidden shared mutable state.

A trade should either:

```text
return updated agent state
```

or otherwise make its state transition explicit.

Do not introduce concurrency concerns into the domain model.

Pekko actors will later own and coordinate mutable runtime state.

---

# Implementation Constraints

Implement only the behavior required by this specification.

Do not introduce:

- random agent selection;
- random winner selection;
- simulation loops;
- Pekko actors for agents;
- trade scheduling;
- taxation;
- redistribution;
- income;
- investment returns;
- inheritance;
- persistence;
- clustering;
- distributed simulation.

YAGNI applies.

---

# Verification

SPEC-002 is complete when:

1. all acceptance criteria are represented by automated tests;
2. all tests pass;
3. `mvn clean verify` succeeds;
4. wealth conservation is verified;
5. negative wealth cannot be produced;
6. the implementation has no Pekko dependency in the domain package;
7. winner selection remains explicit and deterministic;
8. no behavior beyond a single Yard-Sale trade has been introduced.

---

# Out of Scope

The following are intentionally deferred:

```text
random participant selection
random winner selection
seeded randomness
multiple sequential trades
simulation rounds
Pekko actor representation
concurrent trading
simulation coordination
wealth distribution metrics
Gini coefficient
taxation
redistribution
```

A later specification will introduce deterministic random selection and repeated simulation behavior.