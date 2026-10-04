# SPEC-007 — Agent Trade Reservation

## Status

Draft

## Purpose

Prevent an `AgentActor` from participating in more than one active trade at the same time.

This specification introduces a lightweight reservation protocol so that a trade coordinator can temporarily reserve both participants before reading their state and applying a Yard-Sale trade.

The goal is to prevent overlapping coordinators from acting on stale snapshots or interleaving incompatible debit and credit operations.

This specification introduces:

- per-agent trade reservation;
- reservation ownership by trade ID;
- rejection of conflicting reservations;
- release after completion or failure;
- expiry or cleanup of abandoned reservations.

It does not introduce repeated simulation scheduling yet.

---

# Background

SPEC-006 allows one trade to be coordinated correctly in isolation.

However, concurrent coordinators introduce a race.

Example:

```text
Trade T1:
A vs B

Trade T2:
A vs C
```

Both coordinators could initially observe:

```text
A wealth = 100.00
```

and independently calculate their trades.

Without additional coordination:

```text
T1 reads A = 100
T2 reads A = 100

T1 modifies A
T2 modifies A using an outdated assumption
```

The actor itself still processes messages sequentially, but sequential message handling alone does not guarantee that a multi-message trade protocol is isolated from another trade protocol.

SPEC-007 introduces reservation as the isolation boundary.

---

# Core Rule

An `AgentActor` may belong to:

```text
zero active trades
```

or:

```text
exactly one active trade
```

at any given time.

Conceptually:

```text
AVAILABLE
    ↓ Reserve(T1)
RESERVED(T1)
    ↓ Release(T1)
AVAILABLE
```

While:

```text
RESERVED(T1)
```

the agent must reject attempts from another trade:

```text
Reserve(T2)
```

where:

```text
T2 != T1
```

---

# Trade Identity

Every coordinated trade must have a unique trade ID.

Conceptually:

```text
TradeId
```

Example:

```text
trade-000001
trade-000002
```

The exact identifier representation is an implementation decision.

A trade ID must remain unchanged for the lifetime of one trade coordination attempt.

---

# Reservation State

An agent has one of two logical reservation states.

```text
AVAILABLE
```

or:

```text
RESERVED(tradeId)
```

The reservation state is owned by the `AgentActor`.

No external component may mutate it directly.

---

# High-Level Protocol

Before a trade begins:

```text
TradeCoordinator
      |
      | Reserve(T1)
      +----------------> Agent A
      |
      | Reserve(T1)
      +----------------> Agent B
```

Only after both participants acknowledge the reservation may the trade proceed.

Conceptually:

```text
reserve A
    ↓
reserve B
    ↓
both reserved
    ↓
read state
    ↓
calculate trade
    ↓
debit / credit
    ↓
release A
release B
```

If both cannot be reserved, the trade must not proceed.

---

# Acceptance Criteria

## AC-001 — Available agent can be reserved

Given an available agent:

```text
state = AVAILABLE
```

when it receives:

```text
Reserve(T1)
```

then:

```text
state = RESERVED(T1)
```

and the reservation succeeds.

---

## AC-002 — Reservation records its owner

When an agent is reserved by `T1`, the actor must retain enough state to identify:

```text
T1
```

as the active reservation owner.

---

## AC-003 — Conflicting reservation is rejected

Given:

```text
state = RESERVED(T1)
```

when the actor receives:

```text
Reserve(T2)
```

where:

```text
T2 != T1
```

the request must be rejected.

The reservation remains:

```text
RESERVED(T1)
```

---

## AC-004 — Repeated reservation by same trade is idempotent

Given:

```text
state = RESERVED(T1)
```

when:

```text
Reserve(T1)
```

is received again,

the actor should return success without changing state.

This makes reservation retries safe.

---

## AC-005 — Correct owner can release reservation

Given:

```text
state = RESERVED(T1)
```

when:

```text
Release(T1)
```

is received,

then:

```text
state = AVAILABLE
```

---

## AC-006 — Different trade cannot release reservation

Given:

```text
state = RESERVED(T1)
```

when:

```text
Release(T2)
```

is received,

where:

```text
T2 != T1
```

the release must be rejected.

The agent remains:

```text
RESERVED(T1)
```

---

## AC-007 — Release of available agent is safe

If an available agent receives:

```text
Release(T1)
```

the request must not corrupt actor state.

It may be treated as:

```text
no-op success
```

or:

```text
explicit not-reserved response
```

The chosen behavior should be consistent and tested.

---

# Reservation-Gated Trade Operations

Once reservation exists, trade-specific mutation must be associated with that reservation.

The coordinator must not be able to reserve the agent under one trade ID and mutate it using another.

---

## AC-008 — Reserved debit requires matching trade ID

Given:

```text
Agent A = RESERVED(T1)
```

then a trade debit associated with:

```text
T1
```

may proceed if economically valid.

A trade debit associated with:

```text
T2
```

must be rejected.

---

## AC-009 — Reserved credit requires matching trade ID

Given:

```text
Agent A = RESERVED(T1)
```

a trade credit associated with:

```text
T1
```

may proceed.

A trade credit associated with:

```text
T2
```

must be rejected.

---

## AC-010 — Unreserved trade mutation is rejected

Trade-specific debit or credit operations must not be accepted when the agent is:

```text
AVAILABLE
```

unless they are explicitly non-trade administrative operations defined elsewhere.

For the actor-based trade protocol:

```text
reservation
```

must precede:

```text
trade mutation
```

---

# Reservation and State Query

The trade coordinator should read the participant state after successful reservation.

This gives the ordering:

```text
reserve
   ↓
read wealth
   ↓
calculate trade
   ↓
mutate wealth
```

rather than:

```text
read wealth
   ↓
reserve later
```

This reduces the chance of calculating from stale state.

---

## AC-011 — Trade state is read after reservation

The actor-based trade coordinator must not calculate its Yard-Sale stake using participant snapshots obtained before reservation.

Both participants must first be reserved successfully.

---

# Two-Agent Reservation

A trade requires reservation of both participants.

Conceptually:

```text
T1
 |
 +--> Reserve A
 |
 +--> Reserve B
```

The trade proceeds only when:

```text
A reserved by T1
AND
B reserved by T1
```

---

## AC-012 — Trade proceeds only when both reservations succeed

If both agents accept:

```text
Reserve(T1)
```

the coordinator may proceed with the trade.

---

## AC-013 — Partial reservation must be released

If:

```text
Agent A accepts Reserve(T1)
```

but:

```text
Agent B rejects Reserve(T1)
```

then:

```text
Agent A must be released
```

before the trade coordinator terminates or retries.

The trade must not proceed.

---

# Deadlock Avoidance

Two trades may attempt opposite reservation orders.

Example:

```text
T1 wants A then B
T2 wants B then A
```

Without a deterministic rule:

```text
T1 reserves A
T2 reserves B

T1 waits for B
T2 waits for A
```

This creates a distributed deadlock.

Therefore reservation order must be deterministic.

---

## AC-014 — Participants are reserved in deterministic order

For every trade, participants must be reserved according to a stable ordering.

For example:

```text
lowest AgentId first
highest AgentId second
```

Conceptually:

```text
min(agentA.id, agentB.id)
    ↓ reserve first

max(agentA.id, agentB.id)
    ↓ reserve second
```

The exact comparison mechanism depends on the `AgentId` representation.

All trade coordinators must use the same ordering rule.

---

## AC-015 — Coordinator does not hold one reservation indefinitely waiting for another

If the second reservation cannot be acquired within the configured timeout, the first reservation must be released.

---

# Reservation Timeout

A coordinator may fail after successfully reserving an agent.

For example:

```text
coordinator crashes
participant unavailable
unexpected timeout
```

Without cleanup, an agent could remain permanently reserved.

The protocol therefore requires reservation expiry or equivalent cleanup.

---

# Reservation Lease

A reservation should behave conceptually like a lease.

Example:

```text
Reserve(
    tradeId = T1,
    leaseDuration = 5 seconds
)
```

If no valid continuation or release occurs before the lease expires, the agent eventually becomes available again.

The exact timeout value is configuration, not domain behavior.

---

## AC-016 — Reservation cannot remain forever

An abandoned reservation must eventually be released automatically.

The actor must not remain permanently stuck in:

```text
RESERVED(T1)
```

when trade `T1` has disappeared.

---

## AC-017 — Expired reservation returns agent to available state

When the active reservation expires:

```text
RESERVED(T1)
```

must transition to:

```text
AVAILABLE
```

without modifying wealth.

---

## AC-018 — Old expiry message must not clear newer reservation

Consider:

```text
Reserve T1
T1 expires
Reserve T2
late timeout message for T1 arrives
```

The old timeout must not release:

```text
T2
```

Therefore timeout processing must verify the current reservation identity.

Conceptually:

```text
ExpireReservation(T1)
```

only releases the actor if the current reservation is still:

```text
RESERVED(T1)
```

---

# Coordinator Flow

The actor-based trade protocol from SPEC-006 becomes:

```text
TradeCoordinator(T1)
        |
        v
determine reservation order
        |
        v
Reserve first participant
        |
        v
Reserve second participant
        |
     +--+--+
     |     |
success failure
     |     |
     |     +--> release first
     |            ↓
     |        TradeRejected
     |
     v
both participants reserved
        |
        v
GetState from both
        |
        v
calculate Yard-Sale decision
        |
        v
Debit loser(T1)
        |
        v
Credit winner(T1)
        |
        v
Release participants
        |
        v
TradeCompleted
```

---

# Release on Terminal Paths

Every normal terminal path must attempt to release reservations.

This includes:

```text
successful trade
debit rejection
credit failure
successful compensation
invalid trade decision
timeout
cancellation
```

---

## AC-019 — Successful trade releases both agents

After:

```text
TradeCompleted
```

both participant actors must eventually return to:

```text
AVAILABLE
```

---

## AC-020 — Failed trade releases both agents where possible

A normal rejected or compensated trade must not leave its participants reserved.

---

# Compensation Interaction

The compensation behavior introduced in SPEC-006 remains valid.

If:

```text
debit succeeds
credit fails
```

the coordinator should:

```text
compensate loser
```

while it still owns the relevant reservations.

Only after compensation reaches a terminal state should reservation release occur.

Preferred ordering:

```text
debit
   ↓
credit fails
   ↓
compensation
   ↓
release reservations
```

not:

```text
release
   ↓
attempt compensation
```

---

# Suggested Agent Commands

The exact message names are not mandated, but the actor protocol may evolve conceptually toward:

```text
ReserveTrade
ReleaseTrade
GetState
DebitForTrade
CreditForTrade
```

For example:

```java
record ReserveTrade(
    TradeId tradeId,
    ActorRef<ReservationResult> replyTo
) implements Command {
}
```

```java
record ReleaseTrade(
    TradeId tradeId,
    ActorRef<ReleaseResult> replyTo
) implements Command {
}
```

```java
record DebitForTrade(
    TradeId tradeId,
    Wealth amount,
    ActorRef<OperationResult> replyTo
) implements Command {
}
```

```java
record CreditForTrade(
    TradeId tradeId,
    Wealth amount,
    ActorRef<OperationResult> replyTo
) implements Command {
}
```

The exact implementation remains an architectural choice.

---

# Actor State Model

Conceptually:

```text
AgentActor
├── AgentId
├── Wealth
└── Reservation
      ├── AVAILABLE
      └── RESERVED(TradeId)
```

The reservation state is runtime coordination state.

It must not leak into the pure economic domain model unless there is a genuine domain reason.

---

# Domain Independence

Reservation is an actor coordination concern.

The economic domain must remain independent of Apache Pekko.

Do not introduce:

```text
ActorRef
Behavior
reservation mailbox logic
actor timeout logic
```

into the domain model.

Dependency direction remains:

```text
actor
   ↓
domain
```

---

# Suggested Tests

Use Pekko Typed Actor TestKit.

---

## Scenario 1 — Reserve available agent

```text
Given:
Agent A is available

When:
Reserve(T1)

Then:
reservation succeeds

And:
Agent A is reserved by T1
```

---

## Scenario 2 — Reject conflicting reservation

```text
Given:
Agent A is reserved by T1

When:
Reserve(T2)

Then:
reservation is rejected

And:
Agent A remains reserved by T1
```

---

## Scenario 3 — Same trade reservation is idempotent

```text
Given:
Agent A is reserved by T1

When:
Reserve(T1) is sent again

Then:
reservation succeeds

And:
state remains RESERVED(T1)
```

---

## Scenario 4 — Correct release

```text
Given:
Agent A is reserved by T1

When:
Release(T1)

Then:
Agent A becomes available
```

---

## Scenario 5 — Wrong trade cannot release

```text
Given:
Agent A is reserved by T1

When:
Release(T2)

Then:
release is rejected

And:
Agent A remains RESERVED(T1)
```

---

## Scenario 6 — Wrong trade cannot debit

```text
Given:
Agent A is reserved by T1
wealth = 100.00

When:
DebitForTrade(
    T2,
    10.00
)

Then:
debit is rejected

And:
wealth remains 100.00
```

---

## Scenario 7 — Matching trade can debit

```text
Given:
Agent A is reserved by T1
wealth = 100.00

When:
DebitForTrade(
    T1,
    10.00
)

Then:
debit succeeds

And:
wealth = 90.00
```

---

## Scenario 8 — Partial reservation cleanup

```text
Given:
T1 successfully reserves A

And:
B rejects reservation

Then:
A receives Release(T1)

And:
trade does not execute
```

---

## Scenario 9 — Deterministic reservation order

```text
Given:
Agent IDs B and A

When:
a coordinator wants to reserve both

Then:
A is always requested before B
```

The result must not depend on which participant was supplied as `left` or `right`.

---

## Scenario 10 — Reservation expiry

```text
Given:
Agent A is reserved by T1

And:
T1 becomes inactive

When:
the reservation lease expires

Then:
Agent A becomes available

And:
wealth remains unchanged
```

---

## Scenario 11 — Stale expiry does not release current reservation

```text
Given:
T1 reserved A

And:
T1 reservation expired

And:
T2 subsequently reserved A

When:
an old expiry message for T1 is processed

Then:
A remains RESERVED(T2)
```

---

## Scenario 12 — Successful coordinated trade releases both agents

```text
Given:
A and B are reserved by T1

When:
the trade completes successfully

Then:
A is available
And:
B is available
```

---

## Scenario 13 — Compensated trade releases both agents

```text
Given:
T1 debits loser
And:
winner credit fails
And:
compensation succeeds

When:
T1 reaches its terminal failure result

Then:
both participants are released
```

---

# Overlapping Trade Example

Suppose:

```text
T1 = A vs B
T2 = A vs C
```

A valid execution may look like:

```text
T1 -> Reserve(A) : accepted
T2 -> Reserve(A) : rejected

T1 -> Reserve(B) : accepted
T1 -> execute trade
T1 -> release A
T1 -> release B

T2 may retry later
```

At no point does:

```text
A
```

participate in both trades simultaneously.

---

# Deadlock Example

Without deterministic ordering:

```text
T1:
reserves A
waits for B

T2:
reserves B
waits for A
```

Both trades can stall.

With ordering:

```text
A < B
```

both coordinators must attempt:

```text
A first
B second
```

Only one can reserve A.

The other fails early rather than creating circular wait.

---

# Retry Policy

Automatic trade retry is not required by SPEC-007.

If reservation fails because another trade already owns an agent, the coordinator may simply report:

```text
PARTICIPANT_BUSY
```

A later scheduler may decide whether to retry.

Do not introduce arbitrary retries, backoff algorithms, or scheduling behavior into this specification.

---

# Reservation Is Not a Database Lock

The reservation protocol is an actor-level coordination mechanism.

It should not introduce:

```text
synchronized
ReentrantLock
database row locking
distributed lock services
```

Pekko actor state and message handling should implement the protocol.

---

# Persistence

Reservation state remains in memory.

Do not introduce:

```text
Pekko Persistence
durable reservations
database leases
distributed lock storage
```

in this specification.

If an actor system restarts, active reservations may be lost.

Durability is outside current scope.

---

# Relationship to Previous Specifications

The progression is:

```text
SPEC-001
Population initialization
        ↓
SPEC-002
Yard-Sale economic rule
        ↓
SPEC-003
Seeded random selection
        ↓
SPEC-004
Sequential reference simulation
        ↓
SPEC-005
AgentActor state ownership
        ↓
SPEC-006
Single actor trade coordination
        ↓
SPEC-007
Trade reservation and isolation
```

SPEC-007 adds isolation to the actor transaction protocol.

---

# Implementation Constraints

Implement only the reservation behavior required for safe overlapping trade coordination.

Do not introduce:

- full simulation scheduling;
- multiple simulation rounds;
- random scheduler actors;
- throughput optimization;
- fairness queues;
- starvation prevention;
- distributed locking;
- Pekko Cluster;
- cluster sharding;
- persistence;
- taxation;
- redistribution;
- metrics;
- Gini calculations.

YAGNI applies.

---

# Verification

SPEC-007 is complete when:

1. an available agent can be reserved;
2. one active trade owns the reservation;
3. conflicting reservations are rejected;
4. duplicate reservation by the same trade is idempotent;
5. only the owning trade can release the reservation;
6. trade mutations require the matching reservation;
7. both trade participants must be reserved before state is read;
8. partial reservation is cleaned up;
9. all coordinators use deterministic reservation order;
10. abandoned reservations eventually expire;
11. stale expiry messages cannot clear newer reservations;
12. successful and failed trades release reservations;
13. compensation occurs before release;
14. actor tests pass;
15. the domain remains independent of Pekko;
16. `mvn clean verify` succeeds.

---

# Out of Scope

The following are intentionally deferred:

```text
simulation scheduler
repeated actor-based trades
seed-driven actor simulation
trade retry strategy
scheduler fairness
parallel trade throughput
simulation completion detection
result aggregation
Gini coefficient
wealth distribution statistics
taxation
redistribution
Pekko Cluster
cluster sharding
persistence
```

The next specification should use this reservation protocol to execute a complete deterministic actor-based simulation run.