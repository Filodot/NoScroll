# ADR-0001: JVM integration tests for Room persistence

- Status: accepted
- Date: 2026-07-14
- Scope: WP-07 only

## Context

WP-07 requires executable CRUD, transaction, migration and retention tests for the real Room implementation. The WP-00 dependency set contains Room runtime and compiler artifacts, but no environment capable of running Android database code in a local JVM test. Requiring a connected device would make the persistence block dependent on manual infrastructure and would not exercise migrations in the normal CI unit-test job.

## Decision

Add Robolectric 4.16 as a `testImplementation` dependency and enable Android resources for local unit tests, following the official Robolectric Gradle setup. Export Room schemas through the existing KSP compiler into `app/schemas`.

The dependency is test-only. It is not packaged into the debug or release APK and does not change runtime permissions, network behavior or production storage.

## Consequences

- Room CRUD, atomic task grant, migration and retention behavior run in local CI without a phone or emulator.
- The Gradle test dependency graph becomes larger.
- Instrumented tests on a real Android SQLite implementation remain a later release-level validation, not a prerequisite for this isolated data package.
