# Module kotlin-random

Kotlin Multiplatform deterministic pseudo-random generators with mutable and serializable immutable
states.

Use mutable generators when integrating with `kotlin.random.Random` APIs. Use immutable snapshots
when deterministic streams need explicit continuation state, serialization, or checkpointing.

This module is not intended for cryptographic randomness.
