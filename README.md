# wealth-sim-pekko

An agent-based wealth distribution simulator built with Java and Apache Pekko. Its purpose is to explore how simple economic interaction rules can produce emergent wealth distributions and inequality.

**Status: Initial project scaffold**

## Planned direction

- Agents representing economic participants
- Wealth and resource exchange
- Deterministic simulations using seeded randomness
- Wealth conservation invariants
- Wealth-distribution metrics, including the Gini coefficient
- Taxation and redistribution experiments
- Different economic models

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

The current application starts a minimal Pekko Typed actor system and then terminates it cleanly. It does not contain simulation behavior yet.
