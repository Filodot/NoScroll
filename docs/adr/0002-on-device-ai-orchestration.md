# ADR-0002: On-device AI orchestration for the private release

## Status

Accepted for the private, local-account-free release.

## Context

NoScroll needs three external AI providers but the current product has no backend or user accounts.
The owner accepts sending imported learning material to those providers and wants minimal device
resource use.

## Decision

Android orchestrates external inference and stores a validated offline lesson cache. LLM inference
does not run on the device. Provider credentials are entered by the user and encrypted with an
Android Keystore-backed key; they are never bundled in source control or the APK.

The enforcement path must not make network requests. If the cache is empty, the existing local task
engine is used.

## Consequences

- no server cost or account system is required;
- keys can still be extracted by a sufficiently privileged owner of the device, so this is not
  suitable for a public multi-user release;
- public distribution must revisit this ADR and move credentials, quotas and abuse protection to a
  backend;
- PDF parsing, scheduling and validation consume modest local resources, while expensive inference
  remains external;
- provider configuration and health are local and independently replaceable.
