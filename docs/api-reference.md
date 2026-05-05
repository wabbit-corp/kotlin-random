# API Reference

Generated Dokka API docs can be built with:

```bash
./gradlew dokkaGeneratePublicationHtml
```

## Common Result Type

- `RandomResult<Rng, Value>`: immutable generator step result containing a sampled value and the
  continuation generator.

## L64X128

- `L64X128Random`: mutable LXM generator extending `Random`.
- `next64`, `next32`: raw stepping helpers.
- `next64(bound)`, `next32(bound)`: bounded helpers using rejection sampling.
- `fork`, `fork(brine)`: child-generator derivation.
- `asImmutable`: captures mutable state as `L64X128Random.Immutable`.
- `L64X128Random.Immutable`: serializable immutable state with pure equivalents of raw stepping and
  forking helpers.

## Philox

- `PhiloxRandom`: mutable Philox 4x64 exact-counter generator.
- `nextBlock`: returns the next 4x64 Philox block and clears buffered output.
- `advance`: moves the exact counter forward by a number of blocks.
- `jumped`: returns a generator advanced by `jumps * 2^128` blocks.
- `asImmutable`: captures full mutable state, including buffered words and cached 32-bit half.
- `PhiloxRandom.block`: applies the raw Philox bijection to an explicit counter and key.

## Threefry

- `ThreefryRandom`: mutable Threefry 2x32 generator.
- `nextBlock`: returns the next 2x32 block and clears buffered output.
- `split`, `split2`: derive child generators from the current key.
- `foldIn`: derives a generator by folding data into the key.
- `ThreefryRandom.hash`: applies the raw two-word Threefry hash.

## Xoshiro256

- `Xoshiro256PlusPlusRandom`: mutable xoshiro256++ generator.
- `Xoshiro256StarStarRandom`: mutable xoshiro256** generator.
- `jump`: applies the standard xoshiro256 jump polynomial.
- `longJump`: applies the standard xoshiro256 long-jump polynomial.
- `asImmutable`: captures mutable state as a serializable immutable snapshot.

## Error Behavior

Bounded methods require positive bounds. Range methods require `from < until`. Xoshiro explicit
state constructors reject the all-zero state. L64X128 byte seeds must be at most 32 bytes.
