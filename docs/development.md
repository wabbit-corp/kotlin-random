# Development

## Build

```bash
./dev build kotlin-random
```

The build covers JVM, Android library metadata, iOS simulator, iOS device, and host-native targets.

## Documentation Checks

```bash
./dev verify docs kotlin-random
./gradlew -p kotlin-random dokkaGeneratePublicationHtml
```

Public generator APIs should document whether calls mutate state, return continuation state, clear
buffers, or preserve stream alignment.

## Publication Dry Run

```bash
./dev publish --dry-run kotlin-random
```

Generated Gradle metadata comes from `root.clj`. Update the root project definition and rerun
`./dev setup --local kotlin-random` instead of editing generated Gradle files directly.

## Testing Guidance

Keep generator test vectors tied to the exact algorithm and consumption method. Add separate tests
when changing 32-bit, 64-bit, byte, block, jump, fork, or immutable-state behavior.
