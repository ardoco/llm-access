# Files

- [Testing Strategy](strategy.md) - How the llm-access library is tested — hermetic unit tests built on MapEnvironment and @TempDir, Mockito-based layer tests, the Docker-gated Testcontainers REST-Redis integration test plus the live-gated Bifrost smoke test, ArchUnit architecture rules for cache-key completeness and the no-global-environment invariant, LiSSA backward-compatibility guardrails, and the formatting gates.
