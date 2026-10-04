# wealth-sim-pekko

An agent-based wealth distribution simulator built with Java and Apache Pekko. Its purpose is to explore how simple economic interaction rules can produce emergent wealth distributions and inequality.

**Status: SPEC-001 through SPEC-009 MVP implemented**

## Current capabilities

- Equal-wealth population initialization
- Two-decimal Yard-Sale trades
- Deterministic simulations using seeded randomness
- Sequential reference and Pekko Typed actor implementations
- Trade reservation, timeout, and compensation protocols
- Wealth conservation and non-negative wealth invariants
- Distribution metrics including Gini and population-segment shares

Taxation, redistribution, alternative economic models, persistence, clustering, and parallel trade scheduling are intentionally outside the current MVP.

## Build

```bash
mvn clean verify
```

The Maven Wrapper can also be used:

```bash
./mvnw clean verify
```

On Windows:

```powershell
.\mvnw.cmd clean verify
```

## Run

```bash
mvn exec:java
```

The application runs the same deterministic configuration through the sequential and actor implementations, verifies equality by agent identity, prints distribution metrics, and shuts the actor system down cleanly.
