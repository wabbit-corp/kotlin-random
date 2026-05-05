package one.wabbit.random

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Mutable xoshiro256++ generator.
 *
 * The generator exposes Kotlin [Random] primitives plus raw 64-bit/32-bit stepping, jump helpers,
 * and a serializable immutable snapshot form.
 */
class Xoshiro256PlusPlusRandom private constructor(initial: Xoshiro256State) : Random() {
    /** First 64-bit state word. */
    var s0: Long = initial.s0
        private set
    /** Second 64-bit state word. */
    var s1: Long = initial.s1
        private set
    /** Third 64-bit state word. */
    var s2: Long = initial.s2
        private set
    /** Fourth 64-bit state word. */
    var s3: Long = initial.s3
        private set

    /**
     * Creates a generator from explicit state words.
     *
     * The all-zero state is rejected.
     */
    constructor(s0: Long, s1: Long, s2: Long, s3: Long) : this(normalizeXoshiroState(s0, s1, s2, s3))

    /**
     * Creates a generator by expanding [seed] with SplitMix-style seeding.
     */
    constructor(seed: Long) : this(seedXoshiroState(seed))

    override fun nextBits(bitCount: Int): Int = randomBitsFromInt(next32(), bitCount)

    override fun nextInt(): Int = next32()

    override fun nextLong(): Long = next64()

    override fun nextBoolean(): Boolean = next32() < 0

    override fun nextFloat(): Float = randomFloatFromInt(next32())

    override fun nextDouble(): Double = randomDoubleFromLong(next64())

    override fun nextBytes(array: ByteArray, fromIndex: Int, toIndex: Int): ByteArray =
        fillBytesFromLongs(array, fromIndex, toIndex, ::next64)

    /**
     * Returns the next 64-bit xoshiro256++ output and advances this generator.
     */
    fun next64(): Long {
        val result = xoshiro256PlusPlusOutput(s0, s3)
        stepXoshiroState(s0, s1, s2, s3) { nextS0, nextS1, nextS2, nextS3 ->
            s0 = nextS0
            s1 = nextS1
            s2 = nextS2
            s3 = nextS3
        }
        return result
    }

    /**
     * Returns the high 32 bits of the next 64-bit output and advances this generator.
     */
    fun next32(): Int = (next64() ushr 32).toInt()

    /**
     * Advances this generator by the standard xoshiro256 jump polynomial.
     */
    fun jump() {
        assignState(jumpXoshiroState(s0, s1, s2, s3, XOSHIRO256_JUMP))
    }

    /**
     * Advances this generator by the standard xoshiro256 long-jump polynomial.
     */
    fun longJump() {
        assignState(jumpXoshiroState(s0, s1, s2, s3, XOSHIRO256_LONG_JUMP))
    }

    /**
     * Captures this mutable generator as a serializable immutable snapshot.
     */
    fun asImmutable(): Immutable = Immutable(s0, s1, s2, s3)

    private fun assignState(next: Xoshiro256State) {
        s0 = next.s0
        s1 = next.s1
        s2 = next.s2
        s3 = next.s3
    }

    private fun currentState(): Xoshiro256State = Xoshiro256State(s0, s1, s2, s3)

    /**
     * Compares generator state, not object identity.
     */
    override fun equals(other: Any?): Boolean =
        other is Xoshiro256PlusPlusRandom && currentState() == other.currentState()

    /**
     * Returns a hash code derived from the current generator state.
     */
    override fun hashCode(): Int = currentState().hashCode()

    /**
     * Serializable immutable xoshiro256++ generator state.
     *
     * @property s0 first 64-bit state word.
     * @property s1 second 64-bit state word.
     * @property s2 third 64-bit state word.
     * @property s3 fourth 64-bit state word.
     */
    @Serializable
    class Immutable(
        val s0: Long,
        val s1: Long,
        val s2: Long,
        val s3: Long,
    ) {
        init {
            require((s0 or s1 or s2 or s3) != 0L) { XOSHIRO_BAD_STATE }
        }

        internal constructor(initial: Xoshiro256State) : this(initial.s0, initial.s1, initial.s2, initial.s3)

        /**
         * Creates an immutable generator by expanding [seed] with SplitMix-style seeding.
         */
        constructor(seed: Long) : this(seedXoshiroState(seed))

        /**
         * Returns the next 64-bit xoshiro256++ output and the advanced state.
         */
        fun next64(): RandomResult<Immutable, Long> =
            RandomResult(
                xoshiro256PlusPlusOutput(s0, s3),
                Immutable(advanceXoshiroState(s0, s1, s2, s3)),
            )

        /**
         * Returns the high 32 bits of the next output and the advanced state.
         */
        fun next32(): RandomResult<Immutable, Int> {
            val step = next64()
            return RandomResult((step.value ushr 32).toInt(), step.generator)
        }

        /**
         * Returns the state advanced by the standard xoshiro256 jump polynomial.
         */
        fun jump(): Immutable = Immutable(jumpXoshiroState(s0, s1, s2, s3, XOSHIRO256_JUMP))

        /**
         * Returns the state advanced by the standard xoshiro256 long-jump polynomial.
         */
        fun longJump(): Immutable = Immutable(jumpXoshiroState(s0, s1, s2, s3, XOSHIRO256_LONG_JUMP))

        /**
         * Converts this immutable snapshot to a mutable generator with the same state.
         */
        fun asMutable(): Xoshiro256PlusPlusRandom = Xoshiro256PlusPlusRandom(s0, s1, s2, s3)

        private fun state(): Xoshiro256State = Xoshiro256State(s0, s1, s2, s3)

        /**
         * Compares immutable generator state, not object identity.
         */
        override fun equals(other: Any?): Boolean = other is Immutable && state() == other.state()

        /**
         * Returns a hash code derived from the immutable state.
         */
        override fun hashCode(): Int = state().hashCode()
    }

    /**
     * Constructors for mutable xoshiro256++ generators.
     */
    companion object {
        /**
         * Creates a generator by expanding [seed] with SplitMix-style seeding.
         */
        fun seed(seed: Long): Xoshiro256PlusPlusRandom = Xoshiro256PlusPlusRandom(seed)
    }
}

/**
 * Mutable xoshiro256** generator.
 *
 * The generator exposes Kotlin [Random] primitives plus raw 64-bit/32-bit stepping, jump helpers,
 * and a serializable immutable snapshot form.
 */
class Xoshiro256StarStarRandom private constructor(initial: Xoshiro256State) : Random() {
    /** First 64-bit state word. */
    var s0: Long = initial.s0
        private set
    /** Second 64-bit state word. */
    var s1: Long = initial.s1
        private set
    /** Third 64-bit state word. */
    var s2: Long = initial.s2
        private set
    /** Fourth 64-bit state word. */
    var s3: Long = initial.s3
        private set

    /**
     * Creates a generator from explicit state words.
     *
     * The all-zero state is rejected.
     */
    constructor(s0: Long, s1: Long, s2: Long, s3: Long) : this(normalizeXoshiroState(s0, s1, s2, s3))

    /**
     * Creates a generator by expanding [seed] with SplitMix-style seeding.
     */
    constructor(seed: Long) : this(seedXoshiroState(seed))

    override fun nextBits(bitCount: Int): Int = randomBitsFromInt(next32(), bitCount)

    override fun nextInt(): Int = next32()

    override fun nextLong(): Long = next64()

    override fun nextBoolean(): Boolean = next32() < 0

    override fun nextFloat(): Float = randomFloatFromInt(next32())

    override fun nextDouble(): Double = randomDoubleFromLong(next64())

    override fun nextBytes(array: ByteArray, fromIndex: Int, toIndex: Int): ByteArray =
        fillBytesFromLongs(array, fromIndex, toIndex, ::next64)

    /**
     * Returns the next 64-bit xoshiro256** output and advances this generator.
     */
    fun next64(): Long {
        val result = xoshiro256StarStarOutput(s1)
        stepXoshiroState(s0, s1, s2, s3) { nextS0, nextS1, nextS2, nextS3 ->
            s0 = nextS0
            s1 = nextS1
            s2 = nextS2
            s3 = nextS3
        }
        return result
    }

    /**
     * Returns the high 32 bits of the next 64-bit output and advances this generator.
     */
    fun next32(): Int = (next64() ushr 32).toInt()

    /**
     * Advances this generator by the standard xoshiro256 jump polynomial.
     */
    fun jump() {
        assignState(jumpXoshiroState(s0, s1, s2, s3, XOSHIRO256_JUMP))
    }

    /**
     * Advances this generator by the standard xoshiro256 long-jump polynomial.
     */
    fun longJump() {
        assignState(jumpXoshiroState(s0, s1, s2, s3, XOSHIRO256_LONG_JUMP))
    }

    /**
     * Captures this mutable generator as a serializable immutable snapshot.
     */
    fun asImmutable(): Immutable = Immutable(s0, s1, s2, s3)

    private fun assignState(next: Xoshiro256State) {
        s0 = next.s0
        s1 = next.s1
        s2 = next.s2
        s3 = next.s3
    }

    private fun currentState(): Xoshiro256State = Xoshiro256State(s0, s1, s2, s3)

    /**
     * Compares generator state, not object identity.
     */
    override fun equals(other: Any?): Boolean =
        other is Xoshiro256StarStarRandom && currentState() == other.currentState()

    /**
     * Returns a hash code derived from the current generator state.
     */
    override fun hashCode(): Int = currentState().hashCode()

    /**
     * Serializable immutable xoshiro256** generator state.
     *
     * @property s0 first 64-bit state word.
     * @property s1 second 64-bit state word.
     * @property s2 third 64-bit state word.
     * @property s3 fourth 64-bit state word.
     */
    @Serializable
    class Immutable(
        val s0: Long,
        val s1: Long,
        val s2: Long,
        val s3: Long,
    ) {
        init {
            require((s0 or s1 or s2 or s3) != 0L) { XOSHIRO_BAD_STATE }
        }

        internal constructor(initial: Xoshiro256State) : this(initial.s0, initial.s1, initial.s2, initial.s3)

        /**
         * Creates an immutable generator by expanding [seed] with SplitMix-style seeding.
         */
        constructor(seed: Long) : this(seedXoshiroState(seed))

        /**
         * Returns the next 64-bit xoshiro256** output and the advanced state.
         */
        fun next64(): RandomResult<Immutable, Long> =
            RandomResult(
                xoshiro256StarStarOutput(s1),
                Immutable(advanceXoshiroState(s0, s1, s2, s3)),
            )

        /**
         * Returns the high 32 bits of the next output and the advanced state.
         */
        fun next32(): RandomResult<Immutable, Int> {
            val step = next64()
            return RandomResult((step.value ushr 32).toInt(), step.generator)
        }

        /**
         * Returns the state advanced by the standard xoshiro256 jump polynomial.
         */
        fun jump(): Immutable = Immutable(jumpXoshiroState(s0, s1, s2, s3, XOSHIRO256_JUMP))

        /**
         * Returns the state advanced by the standard xoshiro256 long-jump polynomial.
         */
        fun longJump(): Immutable = Immutable(jumpXoshiroState(s0, s1, s2, s3, XOSHIRO256_LONG_JUMP))

        /**
         * Converts this immutable snapshot to a mutable generator with the same state.
         */
        fun asMutable(): Xoshiro256StarStarRandom = Xoshiro256StarStarRandom(s0, s1, s2, s3)

        private fun state(): Xoshiro256State = Xoshiro256State(s0, s1, s2, s3)

        /**
         * Compares immutable generator state, not object identity.
         */
        override fun equals(other: Any?): Boolean = other is Immutable && state() == other.state()

        /**
         * Returns a hash code derived from the immutable state.
         */
        override fun hashCode(): Int = state().hashCode()
    }

    /**
     * Constructors for mutable xoshiro256** generators.
     */
    companion object {
        /**
         * Creates a generator by expanding [seed] with SplitMix-style seeding.
         */
        fun seed(seed: Long): Xoshiro256StarStarRandom = Xoshiro256StarStarRandom(seed)
    }
}

internal data class Xoshiro256State(
    val s0: Long,
    val s1: Long,
    val s2: Long,
    val s3: Long,
)

private const val XOSHIRO_BAD_STATE = "xoshiro256 state must not be all zero"
private val XOSHIRO256_JUMP =
    longArrayOf(
        1733541517147835066L,
        -3051731464161248980L,
        -6244198995065845334L,
        4155657270789760540L,
    )
private val XOSHIRO256_LONG_JUMP =
    longArrayOf(
        8566230491382795199L,
        -4251311993797857357L,
        8606660816089834049L,
        4111957640723818037L,
    )

private fun normalizeXoshiroState(s0: Long, s1: Long, s2: Long, s3: Long): Xoshiro256State {
    require((s0 or s1 or s2 or s3) != 0L) { XOSHIRO_BAD_STATE }
    return Xoshiro256State(s0, s1, s2, s3)
}

private fun advanceXoshiroState(state: Xoshiro256State): Xoshiro256State {
    return advanceXoshiroState(state.s0, state.s1, state.s2, state.s3)
}

private fun advanceXoshiroState(
    s0: Long,
    s1: Long,
    s2: Long,
    s3: Long,
): Xoshiro256State = stepXoshiroState(s0, s1, s2, s3, ::Xoshiro256State)

private fun jumpXoshiroState(state: Xoshiro256State, polynomial: LongArray): Xoshiro256State =
    jumpXoshiroState(state.s0, state.s1, state.s2, state.s3, polynomial)

private fun jumpXoshiroState(
    initialS0: Long,
    initialS1: Long,
    initialS2: Long,
    initialS3: Long,
    polynomial: LongArray,
): Xoshiro256State {
    var acc0 = 0L
    var acc1 = 0L
    var acc2 = 0L
    var acc3 = 0L
    var currentS0 = initialS0
    var currentS1 = initialS1
    var currentS2 = initialS2
    var currentS3 = initialS3

    for (word in polynomial) {
        var bits = word
        repeat(Long.SIZE_BITS) {
            if ((bits and 1L) != 0L) {
                acc0 = acc0 xor currentS0
                acc1 = acc1 xor currentS1
                acc2 = acc2 xor currentS2
                acc3 = acc3 xor currentS3
            }

            stepXoshiroState(currentS0, currentS1, currentS2, currentS3) {
                    nextS0,
                    nextS1,
                    nextS2,
                    nextS3,
                ->
                currentS0 = nextS0
                currentS1 = nextS1
                currentS2 = nextS2
                currentS3 = nextS3
            }
            bits = bits ushr 1
        }
    }

    return Xoshiro256State(acc0, acc1, acc2, acc3)
}

private fun seedXoshiroState(seed: Long): Xoshiro256State {
    var stream = seed

    fun nextSplitMix(): Long {
        stream += WEYL_INCREMENT_64
        var z = stream
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }

    while (true) {
        val candidate =
            Xoshiro256State(
                nextSplitMix(),
                nextSplitMix(),
                nextSplitMix(),
                nextSplitMix(),
            )
        if ((candidate.s0 or candidate.s1 or candidate.s2 or candidate.s3) != 0L) {
            return candidate
        }
    }
}

private fun xoshiro256PlusPlusOutput(s0: Long, s3: Long): Long =
    rotateLeft64(s0 + s3, 23) + s0

private fun xoshiro256StarStarOutput(s1: Long): Long =
    rotateLeft64(s1 * 5L, 7) * 9L

private inline fun <T> stepXoshiroState(
    s0: Long,
    s1: Long,
    s2: Long,
    s3: Long,
    consume: (Long, Long, Long, Long) -> T,
): T {
    val t = s1 shl 17
    val nextS2 = s2 xor s0
    val nextS3 = s3 xor s1
    val nextS1 = s1 xor nextS2
    val nextS0 = s0 xor nextS3

    return consume(nextS0, nextS1, nextS2 xor t, rotateLeft64(nextS3, 45))
}
