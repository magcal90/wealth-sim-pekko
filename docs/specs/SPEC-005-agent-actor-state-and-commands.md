# SPEC-005 — Agent Actor State and Commands

## Status

Draft

## Purpose

Introduce the first Apache Pekko actor into `wealth-sim-pekko`.

Each economic agent may be represented at runtime by an `AgentActor`.

The actor owns the mutable runtime state of exactly one economic agent and serializes all commands that modify or inspect that state.

This specification introduces:

- actor-owned wealth state;
- explicit actor commands;
- querying an agent's wealth;
- crediting wealth;
- debiting wealth;
- rejection of invalid debits;
- actor-level state isolation.

This specification does **not** introduce trade coordination or multiple-agent transactions.

---

# Background

SPEC-001 through SPEC-004 establish a complete deterministic sequential reference implementation without Apache Pekko.

SPEC-005 begins the transition to an actor-based runtime.

The key actor-model rule is:

```text
one AgentActor
    ↓
owns
    ↓
one agent's mutable wealth state
```

No other actor or component may directly modify that actor's wealth.

State changes occur only through messages.

---

# Architectural Principle

The `AgentActor` is responsible for:

```text
state ownership
message processing
serialization of state changes
runtime concurrency isolation
```

It is not responsible for:

```text
selecting trading partners
calculating Yard-Sale stake rules
selecting winners
running simulation loops
collecting system-wide statistics
```

Economic rules already defined in the domain model should remain outside the actor where practical.

---

# Actor Identity

Each `AgentActor` represents exactly one domain agent.

It must have:

```text
AgentId
current wealth
```

The agent identity is immutable for the lifetime of the actor.

The actor's wealth may change only through valid commands.

---

# Initial State

An `AgentActor` must be created with:

```text
agent ID
initial wealth
```

For example:

```text
Agent ID      = agent-42
Initial wealth = 100.00
```

Immediately after creation:

```text
GetState
```

must report:

```text
Agent ID = agent-42
Wealth   = 100.00
```

---

# Commands

The actor must support, at minimum, the following conceptual commands.

```text
GetState
Credit
Debit
```

The exact Java names and message structures are implementation decisions.

Messages should be immutable.

Java records are preferred where appropriate.

---

# Acceptance Criteria

## AC-001 — Actor owns one agent

Each `AgentActor` must represent exactly one agent.

Its identity must not change after actor creation.

---

## AC-002 — Initial wealth is preserved

Given:

```text
agent ID = A
initial wealth = 100.00
```

when the actor starts,

then its current wealth must be:

```text
100.00
```

---

## AC-003 — State can be queried

The caller must be able to request the actor's current state.

A state response must include at least:

```text
agent ID
current wealth
```

Querying the state must not modify it.

---

## AC-004 — Credit increases wealth

Given:

```text
current wealth = 100.00
credit amount  = 25.00
```

when a valid credit command is processed,

then:

```text
new wealth = 125.00
```

The actor must acknowledge successful processing.

---

## AC-005 — Debit decreases wealth

Given:

```text
current wealth = 100.00
debit amount   = 25.00
```

when a valid debit command is processed,

then:

```text
new wealth = 75.00
```

The actor must acknowledge successful processing.

---

## AC-006 — Debit cannot produce negative wealth

Given:

```text
current wealth = 20.00
requested debit = 25.00
```

when the debit command is processed,

then the debit must be rejected.

The actor's wealth must remain:

```text
20.00
```

The actor must respond with a result that clearly indicates rejection.

---

## AC-007 — Debit equal to current wealth is valid

Given:

```text
current wealth = 20.00
requested debit = 20.00
```

when the debit command is processed,

then:

```text
new wealth = 0.00
```

The debit must succeed.

---

## AC-008 — Credit amount must be non-negative

A credit command with a negative amount must be rejected.

For example:

```text
Credit(-10.00)
```

must not modify state.

---

## AC-009 — Debit amount must be non-negative

A debit command with a negative amount must be rejected.

For example:

```text
Debit(-10.00)
```

must not increase or otherwise modify wealth.

---

## AC-010 — Zero-value credit is valid

Given:

```text
current wealth = 100.00
credit = 0.00
```

the command may succeed as a no-op.

The resulting wealth remains:

```text
100.00
```

---

## AC-011 — Zero-value debit is valid

Given:

```text
current wealth = 100.00
debit = 0.00
```

the command may succeed as a no-op.

The resulting wealth remains:

```text
100.00
```

---

## AC-012 — Commands are processed serially

For a single actor, Pekko must serialize command handling.

Given commands:

```text
Credit(10)
Debit(20)
Credit(5)
```

sent to the same actor,

the actor processes them one at a time.

No two handlers for the same actor may concurrently modify its state.

---

## AC-013 — State is private to the actor

No external component may obtain a mutable reference to the actor's internal wealth state.

Responses must expose immutable domain values or snapshots.

---

# Message Model

A possible message hierarchy is:

```java
sealed interface Command {
}
```

with messages conceptually similar to:

```java
record GetState(
    ActorRef<State> replyTo
) implements Command {
}
```

```java
record Credit(
    Wealth amount,
    ActorRef<OperationResult> replyTo
) implements Command {
}
```

```java
record Debit(
    Wealth amount,
    ActorRef<OperationResult> replyTo
) implements Command {
}
```

The exact implementation is not mandated.

---

# Responses

Responses should explicitly represent success or failure.

Conceptually:

```text
OperationAccepted
OperationRejected
```

For a rejection, the response should provide enough information to identify the reason.

For example:

```text
INSUFFICIENT_WEALTH
INVALID_AMOUNT
```

Avoid using exceptions as the normal control flow for expected debit rejection.

---

# Actor State

Conceptually:

```text
AgentActor
├── AgentId
└── Wealth
```

After each successful command, the actor transitions to a new behavior/state representing the updated wealth.

For Pekko Typed, prefer behavior transitions such as:

```text
receive command
    ↓
calculate new state
    ↓
return behavior with new state
```

rather than mutable fields where practical.

---

# Suggested Pekko Structure

The actor may follow a pattern similar to:

```java
public final class AgentActor {

    public sealed interface Command {
    }

    public static Behavior<Command> create(
        AgentId agentId,
        Wealth initialWealth
    ) {
        // ...
    }
}
```

The implementation may use:

```java
Behaviors.setup(...)
Behaviors.receive(...)
```

as appropriate.

Use Apache Pekko Typed APIs.

Do not use the Classic Actor API.

---

# Domain Reuse

Reuse the existing domain value objects introduced by earlier specifications.

For example:

```text
AgentId
Wealth
```

The actor should not introduce a second representation such as:

```java
BigDecimal actorWealth;
```

if a domain `Wealth` type already exists.

Prefer:

```java
Wealth wealth;
```

or behavior state containing `Wealth`.

---

# Domain Independence

The dependency direction must remain:

```text
actor
    ↓
domain
```

not:

```text
domain
    ↓
actor
```

The domain package must remain free of Apache Pekko dependencies.

For example:

```text
com.magcal.wealthsim.domain
```

must not import:

```java
ActorRef
Behavior
ActorSystem
ActorContext
```

The actor layer may import and reuse domain types.

---

# Suggested Tests

Use Pekko Typed Actor TestKit.

---

## Scenario 1 — Initial state

```text
Given:
agent A
initial wealth = 100.00

When:
GetState is sent

Then:
agent ID = A
wealth = 100.00
```

---

## Scenario 2 — Credit

```text
Given:
wealth = 100.00

When:
Credit(25.00) is sent

Then:
operation succeeds

And:
GetState returns 125.00
```

---

## Scenario 3 — Debit

```text
Given:
wealth = 100.00

When:
Debit(25.00) is sent

Then:
operation succeeds

And:
GetState returns 75.00
```

---

## Scenario 4 — Debit insufficient wealth

```text
Given:
wealth = 20.00

When:
Debit(25.00) is sent

Then:
operation is rejected

And:
GetState still returns 20.00
```

---

## Scenario 5 — Debit full balance

```text
Given:
wealth = 20.00

When:
Debit(20.00) is sent

Then:
operation succeeds

And:
wealth = 0.00
```

---

## Scenario 6 — Sequential command processing

```text
Given:
wealth = 100.00

When:
Credit(10.00)
Debit(20.00)
Credit(5.00)

are sent in order

Then:
final wealth = 95.00
```

---

## Scenario 7 — Query does not modify state

```text
Given:
wealth = 100.00

When:
GetState is called repeatedly

Then:
wealth remains 100.00
```

---

## Scenario 8 — Invalid negative credit

```text
Given:
wealth = 100.00

When:
Credit(-10.00) is sent

Then:
operation is rejected

And:
wealth remains 100.00
```

---

## Scenario 9 — Invalid negative debit

```text
Given:
wealth = 100.00

When:
Debit(-10.00) is sent

Then:
operation is rejected

And:
wealth remains 100.00
```

---

# State Transition Example

Conceptually:

```text
AgentActor(A, 100)
        |
        | Debit(20)
        v
AgentActor(A, 80)
        |
        | Credit(10)
        v
AgentActor(A, 90)
```

The actor identity remains the same.

Only its economic state changes.

---

# Concurrency Model

The purpose of actor ownership is to establish this invariant:

```text
Only AgentActor A
may modify Agent A's runtime wealth.
```

Other actors must communicate with `AgentActor A` through messages.

They must not:

```text
read mutable fields directly
modify agent objects directly
share mutable wealth objects
synchronize on agent objects
```

Pekko's mailbox provides serialized command processing for that actor.

---

# Relationship to the Sequential Reference Model

SPEC-004 remains the known-correct sequential implementation.

SPEC-005 does not replace it.

Instead:

```text
SPEC-004
Sequential reference model

        compared against later

SPEC-005+
Actor-based implementation
```

Later actor-based simulation results should preserve the same economic invariants.

---

# No Trade Coordination Yet

A debit and credit may look like the components of a trade, but SPEC-005 must not coordinate a trade between two actors.

For example, do not yet implement:

```text
A Debit
    ↓
B Credit
```

as one transaction.

That requires coordination and failure handling and belongs in a later specification.

SPEC-005 deals only with the behavior of an individual agent actor.

---

# Failure Semantics

Expected domain rejections must not crash the actor.

Examples:

```text
insufficient wealth
negative amount
```

should result in normal rejection responses.

The actor must remain alive and usable after such a rejection.

Unexpected programming errors remain subject to normal Pekko supervision behavior.

Detailed supervision policies are outside this specification.

---

# Persistence

Actor state is in-memory only.

When the actor system terminates, its state may be lost.

Do not introduce:

```text
Pekko Persistence
event sourcing
database storage
snapshots
```

in SPEC-005.

---

# Implementation Constraints

Implement only the behavior required by this specification.

Do not introduce:

- trade coordinators;
- random trade selection inside actors;
- simulation loops;
- transactions spanning multiple actors;
- taxation;
- redistribution;
- metrics;
- Gini calculations;
- clustering;
- cluster sharding;
- Pekko Persistence;
- durable state;
- databases;
- REST APIs.

YAGNI applies.

---

# Verification

SPEC-005 is complete when:

1. an `AgentActor` can be created for one agent;
2. initial wealth is preserved;
3. state can be queried;
4. valid credits succeed;
5. valid debits succeed;
6. overdrafts are rejected;
7. invalid amounts do not modify state;
8. expected rejection does not terminate the actor;
9. commands are serialized by the actor;
10. actor state is not exposed as mutable shared state;
11. Pekko Typed Actor TestKit tests pass;
12. domain classes remain independent of Pekko;
13. `mvn clean verify` succeeds.

---

# Out of Scope

The following are intentionally deferred:

```text
coordinating a trade between two AgentActors
atomic multi-actor operations
trade transaction protocol
random participant selection through actors
simulation scheduling
parallel simulation execution
metrics collection
Gini coefficient
taxation
redistribution
actor persistence
Pekko Cluster
cluster sharding
```

The next specification will introduce coordination of a single Yard-Sale trade between two `AgentActor` instances.