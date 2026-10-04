# SPEC-006 — Actor Trade Coordination

## Status

Draft

## Purpose

Introduce coordination of a single Yard-Sale trade between two `AgentActor` instances.

The trade must preserve the economic behavior defined in SPEC-002 while respecting the actor ownership model introduced in SPEC-005.

This specification introduces:

- a dedicated trade coordinator;
- querying participant state through actor messages;
- calculating the Yard-Sale stake using domain logic;
- debiting the loser;
- crediting the winner;
- completing or rejecting one coordinated trade;
- preserving wealth invariants across two independently owned actors.

This specification does not introduce repeated simulation runs or parallel trade scheduling.

---

# Background

SPEC-005 establishes:

```text
AgentActor A
owns wealth for Agent A

AgentActor B
owns wealth for Agent B
```

Neither actor may directly modify the other's state.

A Yard-Sale trade therefore becomes a coordinated interaction:

```text
                  TradeCoordinator
                  /              \
                 /                \
                v                  v
         AgentActor A        AgentActor B
```

The coordinator is responsible for orchestrating the interaction.

The economic rules themselves remain defined by the domain model from SPEC-002.

---

# Core Principle

A trade involves two kinds of responsibility.

The domain decides:

```text
how much is at stake
who gains wealth
who loses wealth
whether the economic result is valid
```

The actor layer decides:

```text
how state is queried
how commands are ordered
how acknowledgements are handled
how failure is coordinated
```

These responsibilities must remain separate.

---

# Trade Inputs

A coordinated trade requires:

```text
left AgentActor reference
right AgentActor reference
winner
stake percentage
```

The winner must correspond to one of the two participants.

Participant selection and winner selection remain outside this specification.

---

# High-Level Flow

A successful trade should conceptually follow:

```text
TradeCoordinator
       |
       | GetState
       +----------------> Agent A
       |
       | GetState
       +----------------> Agent B
       |
       | receive states
       v
calculate stake using SPEC-002
       |
       | Debit(stake)
       +----------------> Loser
       |
       | DebitAccepted
       v
       | Credit(stake)
       +----------------> Winner
       |
       | CreditAccepted
       v
TradeCompleted
```

---

# Acceptance Criteria

## AC-001 — Trade requires two distinct actors

The coordinator must reject an attempt to trade an agent with itself.

Conceptually:

```text
Agent A vs Agent A
```

is invalid.

---

## AC-002 — Participant states are obtained through messages

The coordinator must not directly inspect mutable actor state.

It must obtain participant wealth using actor communication.

For example:

```text
GetState
```

messages sent to both participants.

---

## AC-003 — Stake follows SPEC-002

The coordinator must use the Yard-Sale rule already defined by the domain.

Given:

```text
Agent A wealth = 100.00
Agent B wealth = 50.00
stake percentage = 10%
winner = A
```

the stake must be:

```text
5.00
```

The coordinator must not introduce a second independent implementation of the stake formula.

---

## AC-004 — Loser is debited first

For a non-zero trade, the loser must be debited before the winner is credited.

Conceptually:

```text
Debit loser
    ↓
DebitAccepted
    ↓
Credit winner
```

The winner must not be credited before the loser confirms the debit.

---

## AC-005 — Winner receives exactly the debited amount

If:

```text
stake = 5.00
```

then:

```text
loser debit = 5.00
winner credit = 5.00
```

The credited amount must exactly match the successfully debited amount.

---

## AC-006 — Successful trade preserves wealth

For a successful trade:

```text
A.before + B.before
=
A.after + B.after
```

For example:

```text
before:
A = 100.00
B = 50.00

after:
A = 105.00
B = 45.00
```

Total wealth remains:

```text
150.00
```

---

## AC-007 — Successful trade reports completion

The requester must receive an explicit successful result.

Conceptually:

```text
TradeCompleted
```

The result should expose enough information to inspect the trade.

At minimum:

```text
left agent ID
right agent ID
winner
stake
```

---

## AC-008 — Zero-stake trade completes without state change

If either participant has zero wealth, the Yard-Sale stake may be zero.

Example:

```text
A = 100.00
B = 0.00
stake percentage = 10%
```

results in:

```text
stake = 0.00
```

The trade may complete successfully without sending unnecessary debit or credit commands.

Both wealth values remain unchanged.

---

## AC-009 — Rejected debit prevents credit

If the loser rejects the debit:

```text
DebitRejected
```

then the winner must not be credited.

The trade must report failure.

This preserves:

```text
no debit
⇒
no credit
```

---

## AC-010 — Trade failure is explicit

Expected trade failures must produce a clear result.

Conceptually:

```text
TradeRejected
```

Possible reasons may include:

```text
INVALID_PARTICIPANTS
INVALID_WINNER
DEBIT_REJECTED
PARTICIPANT_UNAVAILABLE
```

The exact representation is an implementation decision.

---

## AC-011 — Coordinator does not own participant wealth

The `TradeCoordinator` must not maintain an authoritative copy of agent balances.

Agent wealth remains owned by:

```text
AgentActor
```

The coordinator may hold temporary snapshots only for the duration of the trade.

---

## AC-012 — One coordinator handles one trade

A trade coordinator instance should coordinate exactly one trade.

After reaching either:

```text
TradeCompleted
```

or:

```text
TradeRejected
```

the coordinator should stop.

This keeps coordination state local to one transaction.

---

# Important Failure Case

The critical distributed-state problem is:

```text
loser debit succeeds
winner credit fails
```

At that point:

```text
total wealth has temporarily decreased
```

This specification must define how that case is handled.

---

# Compensation Rule

If the loser has been successfully debited but the winner cannot be credited, the coordinator must attempt compensation by crediting the same amount back to the loser.

Conceptually:

```text
Debit loser
    ↓
DebitAccepted
    ↓
Credit winner
    ↓
CreditRejected / unavailable
    ↓
Compensate loser
    ↓
Credit(stake)
```

The goal is to restore the pre-trade economic state.

---

# AC-013 — Credit failure triggers compensation

Given:

```text
loser debit succeeds
winner credit fails
```

the coordinator must send a compensating credit to the loser for exactly the debited amount.

---

# AC-014 — Successful compensation restores wealth

If compensation succeeds:

```text
loser wealth after compensation
=
loser wealth before trade
```

and:

```text
winner wealth remains unchanged
```

The final trade result must be failure, not success.

---

# AC-015 — Compensation failure must be surfaced

If:

```text
debit succeeds
credit fails
compensation also fails
```

the coordinator must report a distinct failure state.

For example:

```text
COMPENSATION_FAILED
```

The failure must not be silently treated as a normal rejected trade.

This represents a broken economic invariant requiring higher-level handling.

---

# Trade State Machine

A trade coordinator may naturally be represented as a small state machine.

Conceptually:

```text
START
  |
  v
WAITING_FOR_STATES
  |
  v
CALCULATING
  |
  v
WAITING_FOR_DEBIT
  |
  +--------------------+
  |                    |
DebitRejected       DebitAccepted
  |                    |
  v                    v
FAILED          WAITING_FOR_CREDIT
                       |
             +---------+---------+
             |                   |
      CreditAccepted       CreditFailed
             |                   |
             v                   v
         COMPLETED       COMPENSATING
                                 |
                         +-------+-------+
                         |               |
                   Compensated     CompensationFailed
                         |               |
                         v               v
                       FAILED       INCONSISTENT
```

The exact implementation may differ, but the behavior must preserve the same semantics.

---

# Suggested Actor Structure

Conceptually:

```java
public final class TradeCoordinator {

    public sealed interface Command {
    }

    public static Behavior<Command> create(
        ActorRef<AgentActor.Command> left,
        ActorRef<AgentActor.Command> right,
        Winner winner,
        StakePercentage stakePercentage,
        ActorRef<TradeResult> replyTo
    ) {
        // ...
    }
}
```

The coordinator may use message adapters where appropriate to convert agent responses into its own protocol.

---

# Message Adapters

Because `AgentActor` returns its own response types, the coordinator may use Pekko Typed message adapters.

Conceptually:

```text
AgentActor.State
      ↓
message adapter
      ↓
TradeCoordinator.Command
```

This keeps the coordinator protocol explicit and type-safe.

---

# Domain Reuse

The coordinator must reuse the domain behavior from SPEC-002.

Conceptually:

```text
participant snapshots
        ↓
domain Yard-Sale calculation
        ↓
TradeDecision
```

The actor layer must not duplicate:

```text
stake percentage × poorer wealth
```

logic in ad hoc actor code.

---

# Suggested Domain Result

The domain calculation may produce something conceptually like:

```text
TradeDecision
├── winner
├── loser
└── stake
```

This decision can then drive actor messaging.

The exact class name is not prescribed.

---

# Suggested Tests

Use Pekko Typed Actor TestKit.

---

## Scenario 1 — Successful richer-agent win

```text
Given:
Agent A = 100.00
Agent B = 50.00
stake = 10%
winner = A

When:
one actor trade is coordinated

Then:
A = 105.00
B = 45.00
trade completes
stake = 5.00
total wealth = 150.00
```

---

## Scenario 2 — Successful poorer-agent win

```text
Given:
Agent A = 100.00
Agent B = 50.00
stake = 10%
winner = B

When:
the trade completes

Then:
A = 95.00
B = 55.00
total wealth = 150.00
```

---

## Scenario 3 — Equal wealth

```text
Given:
A = 100.00
B = 100.00
stake = 10%
winner = A

Then:
stake = 10.00
A = 110.00
B = 90.00
```

---

## Scenario 4 — Zero-wealth participant

```text
Given:
A = 100.00
B = 0.00

When:
the trade executes

Then:
stake = 0.00
A remains 100.00
B remains 0.00
trade completes successfully
```

---

## Scenario 5 — Debit rejection

Using a controlled test actor or probe:

```text
Given:
loser rejects Debit

When:
trade coordination occurs

Then:
winner receives no Credit command
And:
trade is rejected
```

---

## Scenario 6 — Credit failure compensation

Using controlled actors:

```text
Given:
loser accepts Debit(5.00)

And:
winner rejects Credit(5.00)

When:
the trade is coordinated

Then:
loser receives compensating Credit(5.00)
And:
trade reports failure
```

---

## Scenario 7 — Compensation succeeds

```text
Given:
debit succeeds
credit fails
compensation succeeds

Then:
participant wealth is restored to the pre-trade state
And:
trade result = rejected
```

---

## Scenario 8 — Compensation fails

```text
Given:
debit succeeds
credit fails
compensation fails

Then:
trade reports COMPENSATION_FAILED
```

The coordinator must not claim the trade completed successfully.

---

## Scenario 9 — Self trade

```text
Given:
the same AgentActor is supplied as both participants

When:
trade coordination is requested

Then:
trade is rejected before wealth modification
```

---

## Scenario 10 — Coordinator termination

```text
Given:
a trade reaches TradeCompleted

Then:
the coordinator stops
```

Likewise:

```text
Given:
a trade reaches a terminal failure state

Then:
the coordinator stops
```

---

# Actor Ownership

The following invariant must remain true:

```text
AgentActor A
is the only runtime owner
of Agent A wealth
```

The coordinator:

```text
requests state
sends commands
tracks temporary transaction state
```

but never directly modifies participant wealth.

---

# Concurrency

SPEC-006 coordinates one trade only.

It does not yet solve the problem of:

```text
Trade 1 using Agent A
running concurrently with
Trade 2 also using Agent A
```

That issue is intentionally deferred.

Therefore, SPEC-006 proves:

```text
one coordinated trade is correct
```

not:

```text
many overlapping trades are safe
```

Concurrent scheduling will require additional rules.

---

# Important Snapshot Consideration

The coordinator may initially query:

```text
A wealth
B wealth
```

and calculate a stake.

Those values are snapshots.

If another trade modifies one of the agents before the debit occurs, the snapshot may become stale.

SPEC-006 avoids solving this broader concurrency problem by limiting itself to isolated trade coordination.

A later specification must define how overlapping trades are prevented or serialized.

---

# Timeouts

Actor requests may fail to receive responses.

The coordinator should not wait indefinitely.

Timeout handling should exist for states such as:

```text
waiting for participant state
waiting for debit acknowledgement
waiting for credit acknowledgement
waiting for compensation acknowledgement
```

A timeout should produce an explicit trade failure.

The timeout duration itself should be configurable or centrally defined rather than scattered through actor code.

---

# AC-016 — Coordinator must terminate on timeout

If a required participant response does not arrive within the configured timeout:

```text
trade must fail
```

and:

```text
coordinator must terminate
```

If a debit has already succeeded, compensation rules apply where possible.

---

# Failure Semantics

Expected failures should be represented as messages/results, not exceptions.

Examples include:

```text
debit rejected
invalid participant
timeout
credit rejected
```

Unexpected programming errors remain subject to normal Pekko supervision behavior.

---

# Persistence

Trade coordination remains entirely in memory.

Do not introduce:

```text
Pekko Persistence
event sourcing
durable state
databases
```

in SPEC-006.

---

# Relationship to Previous Specifications

The dependency flow is:

```text
SPEC-001
Population and wealth model
      ↓
SPEC-002
Yard-Sale economic rule
      ↓
SPEC-003
Seeded selection
      ↓
SPEC-004
Sequential reference simulation
      ↓
SPEC-005
Individual AgentActor
      ↓
SPEC-006
Single actor-coordinated trade
```

SPEC-004 remains the reference implementation for economic correctness.

---

# Implementation Constraints

Implement only what is needed for one coordinated actor-based trade.

Do not introduce:

- repeated actor-based simulation runs;
- random participant selection inside the coordinator;
- global trade scheduling;
- concurrent trade execution;
- locking across many agents;
- taxation;
- redistribution;
- income;
- Gini calculation;
- metrics aggregation;
- persistence;
- Pekko Cluster;
- cluster sharding.

YAGNI applies.

---

# Verification

SPEC-006 is complete when:

1. two distinct `AgentActor` instances can participate in one trade;
2. states are obtained only through actor messages;
3. the stake is calculated using existing domain logic;
4. the loser is debited before the winner is credited;
5. successful trades preserve total wealth;
6. debit rejection prevents credit;
7. credit failure triggers compensation;
8. successful compensation restores the original state;
9. compensation failure is explicitly reported;
10. zero-stake trades are handled correctly;
11. coordinator timeouts are handled;
12. the coordinator terminates after reaching a terminal result;
13. actor tests pass;
14. domain classes remain independent of Pekko;
15. `mvn clean verify` succeeds.

---

# Out of Scope

The following are intentionally deferred:

```text
multiple simultaneous trades
agent reservation / locking
overlapping-trade conflict handling
simulation scheduling
repeated actor-based execution
trade throughput
metrics collection
Gini coefficient
taxation
redistribution
Pekko Persistence
Pekko Cluster
cluster sharding
```

The next specification should define how an agent is protected from participating in multiple overlapping trades before building the complete actor-based simulation loop.