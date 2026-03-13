package one.wabbit.random

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.random.Random

/**
 * Mutable LXM generator with the same stepping, seeding, split, and core primitive-generation
 * behavior as Java's `L64X128MixRandom`. Byte-array seeding uses unsigned byte packing, matching
 * current OpenJDK behavior after the JDK-8282144 fix, but rejects seeds longer than 32 bytes
 * instead of silently truncating them.
 */
class L64X128Random private constructor(initial: L64X128State) : Random() {
    val a: Long = initial.a
    var s: Long = initial.s
        private set
    var x0: Long = initial.x0
        private set
    var x1: Long = initial.x1
        private set

    constructor(a: Long, s: Long, x0: Long, x1: Long) : this(normalizeState(a, s, x0, x1))

    constructor(seed: Long) : this(longSeedState(seed))

    constructor(seed: ByteArray) : this(byteSeedState(seed))

    override fun nextBits(bitCount: Int): Int = randomBitsFromInt(next32(), bitCount)

    override fun nextInt(): Int = next32()

    override fun nextInt(until: Int): Int = next32(until)

    override fun nextInt(from: Int, until: Int): Int {
        require(from < until) { BAD_RANGE }

        val width = until - from
        if (width > 0) {
            return from + next32(width)
        }
        if (width == Int.MIN_VALUE) {
            return from + (next32() and Int.MAX_VALUE)
        }

        while (true) {
            val candidate = next32()
            if (candidate in from until until) {
                return candidate
            }
        }
    }

    override fun nextLong(): Long = next64()

    override fun nextLong(until: Long): Long = next64(until)

    override fun nextLong(from: Long, until: Long): Long {
        require(from < until) { BAD_RANGE }

        val width = until - from
        if (width > 0L) {
            return from + next64(width)
        }
        if (width == Long.MIN_VALUE) {
            return from + (next64() and Long.MAX_VALUE)
        }

        while (true) {
            val candidate = next64()
            if (candidate in from until until) {
                return candidate
            }
        }
    }

    override fun nextBoolean(): Boolean = next32() < 0

    override fun nextFloat(): Float = randomFloatFromInt(next32())

    override fun nextDouble(): Double = randomDoubleFromLong(next64())

    override fun nextBytes(array: ByteArray, fromIndex: Int, toIndex: Int): ByteArray =
        fillBytesFromLongs(array, fromIndex, toIndex, ::next64)

    fun next64(): Long {
        val result = mixLea64(s + x0)
        val nextS = LCG_MULTIPLIER * s + a
        val x = x1 xor x0
        val nextX0 = rotateLeft64(x0, 24) xor x xor (x shl 16)
        val nextX1 = rotateLeft64(x, 37)

        s = nextS
        x0 = nextX0
        x1 = nextX1

        return result
    }

    fun next64(bound: Long): Long {
        require(bound > 0L) { BAD_BOUND }

        val mask = bound - 1
        var raw = next64()
        if ((bound and mask) == 0L) {
            return raw and mask
        }

        var sample = raw ushr 1
        while (true) {
            val candidate = sample % bound
            if (sample + mask - candidate >= 0L) {
                return candidate
            }
            raw = next64()
            sample = raw ushr 1
        }
    }

    fun next32(): Int = (next64() ushr 32).toInt()

    fun next32(bound: Int): Int {
        require(bound > 0) { BAD_BOUND }

        val mask = bound - 1
        var raw = next32()
        if ((bound and mask) == 0) {
            return raw and mask
        }

        var sample = raw ushr 1
        while (true) {
            val candidate = sample % bound
            if (sample + mask - candidate >= 0) {
                return candidate
            }
            raw = next32()
            sample = raw ushr 1
        }
    }

    fun fork(): L64X128Random = fork(next64())

    fun fork(brine: Long): L64X128Random = L64X128Random(brine shl 1, next64(), next64(), next64())

    fun asImmutable(): Immutable = Immutable(L64X128State(a, s, x0, x1))

    private fun currentState(): L64X128State = L64X128State(a, s, x0, x1)

    override fun equals(other: Any?): Boolean = other is L64X128Random && currentState() == other.currentState()

    override fun hashCode(): Int = currentState().hashCode()

    @Serializable
    class Immutable internal constructor(
        val a: Long,
        val s: Long,
        val x0: Long,
        val x1: Long,
        @Transient private val unused: Int = 0,
    ) {
        internal constructor(initial: L64X128State) : this(
            a = initial.a,
            s = initial.s,
            x0 = initial.x0,
            x1 = initial.x1,
            unused = 0,
        )

        constructor(a: Long, s: Long, x0: Long, x1: Long) : this(normalizeState(a, s, x0, x1))

        constructor(seed: Long) : this(longSeedState(seed))

        constructor(seed: ByteArray) : this(byteSeedState(seed))

        fun next64(): RandomResult<Immutable, Long> {
            val result = mixLea64(s + x0)
            return RandomResult(result, Immutable(advancedState(a, s, x0, x1)))
        }

        fun next64(bound: Long): RandomResult<Immutable, Long> {
            require(bound > 0L) { BAD_BOUND }

            val mask = bound - 1
            var step = next64()
            if ((bound and mask) == 0L) {
                return RandomResult(step.value and mask, step.generator)
            }

            var sample = step.value ushr 1
            var state = step.generator
            while (true) {
                val candidate = sample % bound
                if (sample + mask - candidate >= 0L) {
                    return RandomResult(candidate, state)
                }
                step = state.next64()
                sample = step.value ushr 1
                state = step.generator
            }
        }

        fun next32(): RandomResult<Immutable, Int> {
            val step = next64()
            return RandomResult((step.value ushr 32).toInt(), step.generator)
        }

        fun next32(bound: Int): RandomResult<Immutable, Int> {
            require(bound > 0) { BAD_BOUND }

            val mask = bound - 1
            var step = next32()
            if ((bound and mask) == 0) {
                return RandomResult(step.value and mask, step.generator)
            }

            var sample = step.value ushr 1
            var state = step.generator
            while (true) {
                val candidate = sample % bound
                if (sample + mask - candidate >= 0) {
                    return RandomResult(candidate, state)
                }
                step = state.next32()
                sample = step.value ushr 1
                state = step.generator
            }
        }

        fun fork(): RandomResult<Immutable, Immutable> {
            val brineStep = next64()
            return brineStep.generator.fork(brineStep.value)
        }

        fun fork(brine: Long): RandomResult<Immutable, Immutable> {
            val sStep = next64()
            val x0Step = sStep.generator.next64()
            val x1Step = x0Step.generator.next64()
            return RandomResult(
                Immutable(brine shl 1, sStep.value, x0Step.value, x1Step.value),
                x1Step.generator,
            )
        }

        fun asMutable(): L64X128Random = L64X128Random(L64X128State(a, s, x0, x1))

        private fun state(): L64X128State = L64X128State(a, s, x0, x1)

        override fun equals(other: Any?): Boolean = other is Immutable && state() == other.state()

        override fun hashCode(): Int = state().hashCode()

        companion object {
            fun seed(bytes: ByteArray): Immutable = Immutable(bytes)
        }
    }

    companion object {
        private const val BAD_BOUND = "bound must be positive"
        private const val BAD_RANGE = "from must be less than until"
        private const val BAD_BYTE_SEED =
            "byte seed must be at most ${4 * Long.SIZE_BYTES} bytes for L64X128Random"
        private const val SILVER_RATIO_64 = 7640891576956012809L
        private const val LCG_MULTIPLIER = -3372029247567499371L
        private const val LEA_MULTIPLIER = -2685821657736338717L
        private const val MURMUR_MIX1 = -49064778989728563L
        private const val MURMUR_MIX2 = -4265267296055464877L
        private const val STAFFORD_MIX1 = -4658895280553007687L
        private const val STAFFORD_MIX2 = -7723592293110705685L

        fun seed(bytes: ByteArray): L64X128Random = L64X128Random(bytes)

        private fun longSeedState(seed: Long): L64X128State {
            val scrambled = seed xor SILVER_RATIO_64
            return normalizeState(
                mixMurmur64(scrambled),
                1L,
                mixStafford13(scrambled),
                mixStafford13(scrambled + WEYL_INCREMENT_64),
            )
        }

        private fun byteSeedState(seed: ByteArray): L64X128State {
            val words = seedWords(seed)
            return normalizeState(words[0], words[1], words[2], words[3])
        }

        private fun seedWords(seed: ByteArray): LongArray {
            require(seed.size <= 4 * Long.SIZE_BYTES) { BAD_BYTE_SEED }

            val words = LongArray(4)
            val used = seed.size

            // OpenJDK packs seed bytes into longs in big-endian order even though generated bytes
            // are later emitted little-endian from `nextLong()`-style outputs.
            for (index in 0 until used) {
                val wordIndex = index shr 3
                words[wordIndex] = (words[wordIndex] shl 8) or (seed[index].toLong() and 0xffL)
            }

            var fillIndex = (used + 7) shr 3
            var stream = words[0]
            while (fillIndex < words.size) {
                stream += SILVER_RATIO_64
                words[fillIndex] = mixMurmur64(stream)
                fillIndex += 1
            }

            if ((words[2] or words[3]) == 0L) {
                var repair = words[0] and -2L
                for (index in 2 until words.size) {
                    repair += SILVER_RATIO_64
                    words[index] = mixMurmur64(repair)
                }
            }

            return words
        }

        private fun normalizeState(a: Long, s: Long, x0: Long, x1: Long): L64X128State {
            val oddA = a or 1L
            if ((x0 or x1) != 0L) {
                return L64X128State(oddA, s, x0, x1)
            }

            val repairedSeed = s + WEYL_INCREMENT_64
            return L64X128State(
                oddA,
                s,
                mixStafford13(repairedSeed),
                mixStafford13(repairedSeed + WEYL_INCREMENT_64),
            )
        }

        private fun advancedState(a: Long, s: Long, x0: Long, x1: Long): L64X128State {
            val x = x1 xor x0
            return L64X128State(
                a,
                LCG_MULTIPLIER * s + a,
                rotateLeft64(x0, 24) xor x xor (x shl 16),
                rotateLeft64(x, 37),
            )
        }

        private fun mixMurmur64(value: Long): Long {
            var mixed = value
            mixed = (mixed xor (mixed ushr 33)) * MURMUR_MIX1
            mixed = (mixed xor (mixed ushr 33)) * MURMUR_MIX2
            return mixed xor (mixed ushr 33)
        }

        private fun mixStafford13(value: Long): Long {
            var mixed = value
            mixed = (mixed xor (mixed ushr 30)) * STAFFORD_MIX1
            mixed = (mixed xor (mixed ushr 27)) * STAFFORD_MIX2
            return mixed xor (mixed ushr 31)
        }

        private fun mixLea64(value: Long): Long {
            var mixed = value
            mixed = (mixed xor (mixed ushr 32)) * LEA_MULTIPLIER
            mixed = (mixed xor (mixed ushr 32)) * LEA_MULTIPLIER
            return mixed xor (mixed ushr 32)
        }

    }
}

internal data class L64X128State(
    val a: Long,
    val s: Long,
    val x0: Long,
    val x1: Long,
)
