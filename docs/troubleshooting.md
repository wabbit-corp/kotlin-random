# Troubleshooting

## Streams Differ After Refactoring

Check the consumption pattern. Calling `next32`, `next64`, `nextBytes`, and block APIs in different
orders can change alignment. This is intentional for generators that expose exact block behavior.

## Immutable Samples Repeat

Use the returned continuation generator:

```kotlin
val first = rng.next64()
val second = first.generator.next64()
```

Calling `rng.next64()` repeatedly on the same immutable state returns the same first value.

## Xoshiro Rejects Explicit State

The all-zero xoshiro256 state is invalid. Use a non-zero explicit state or a `Long` seed
constructor.

## L64X128 Rejects a Byte Seed

`L64X128Random` accepts at most 32 seed bytes, matching its four 64-bit state words. Shorter seeds
are expanded deterministically.

## Results Are Not Suitable for Secrets

These are deterministic pseudo-random generators for reproducibility, simulation, and testing. Do
not use them to generate passwords, tokens, keys, or other security-sensitive secrets.
