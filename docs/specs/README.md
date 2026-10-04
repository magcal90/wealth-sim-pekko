# Specifications

Behavior will be introduced through numbered specifications, for example:

- `SPEC-001-initialize-population.md`
- `SPEC-002-yard-sale-trade.md`
- `SPEC-003-...`

Each specification will follow this workflow:

```text
Specification
    ↓
Acceptance criteria
    ↓
Tests
    ↓
Implementation
    ↓
Verification
```

A specification should describe what the system must do before implementation details are introduced.

Where practical:

+ acceptance criteria should be observable and testable;

+ tests should be derived from the specification;

+ domain behavior should remain independent of Apache Pekko;

+ Pekko should be used for actor state, messaging, concurrency, and orchestration where required;

+ implementation should remain limited to what the current specification needs.

Economic behavior is intentionally outside the initial project scaffold and will be introduced through subsequent numbered specifications.
