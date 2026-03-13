package one.wabbit.random

import kotlinx.serialization.Serializable
import kotlin.random.Random

private const val PHILOX_WORDS_PER_BLOCK = 4

/**
 * Mutable Philox 4x64 generator with a consistent exact-counter API.
 *
 * The stored counter is the exact 256-bit counter that will be hashed next, so
 * `PhiloxRandom(key0 = 0, key1 = 0).nextBlock()` and `PhiloxRandom.block(0, 0, 0, 0, 0, 0)`
 * refer to the same block. NumPy's exposed `Philox` state instead stores the last-used counter,
 * so `numpy.random.Philox(counter=[0, 0, 0, 0], key=[0, 0]).random_raw(4)` corresponds to this
 * library with `counter0 = 1`. `next32()` follows NumPy's low-32/high-32 caching behavior, while
 * `next64()` stays aligned to 64-bit words and discards any cached 32-bit half. Full restore
 * constructors that include buffered words and cached 32-bit state are internal; use
 * `asImmutable()` / `asMutable()` to round-trip those opaque snapshots. The higher-level
 * `Random` primitives follow the same alignment rules, so `nextDouble()` and `nextBytes(...)`
 * are also 64-bit aligned.
 */
class PhiloxRandom private constructor(initial: PhiloxState) : Random() {
    val key0: Long = initial.key0
    val key1: Long = initial.key1
    var counter0: Long = initial.counter0
        private set
    var counter1: Long = initial.counter1
        private set
    var counter2: Long = initial.counter2
        private set
    var counter3: Long = initial.counter3
        private set

    private var block0: Long = initial.block0
    private var block1: Long = initial.block1
    private var block2: Long = initial.block2
    private var block3: Long = initial.block3
    private var blockIndex: Int = initial.blockIndex
    private var hasUInt32: Boolean = initial.hasUInt32
    private var uinteger: Int = initial.uinteger

    constructor(
        key0: Long,
        key1: Long,
        counter0: Long = 0L,
        counter1: Long = 0L,
        counter2: Long = 0L,
        counter3: Long = 0L,
    ) : this(
        validateState(
            PhiloxState(
                key0 = key0,
                key1 = key1,
                counter0 = counter0,
                counter1 = counter1,
                counter2 = counter2,
                counter3 = counter3,
            ),
        ),
    )

    internal constructor(
        key0: Long,
        key1: Long,
        counter0: Long,
        counter1: Long,
        counter2: Long,
        counter3: Long,
        block0: Long = 0L,
        block1: Long = 0L,
        block2: Long = 0L,
        block3: Long = 0L,
        blockIndex: Int,
        hasUInt32: Boolean,
        uinteger: Int,
    ) : this(
        validateState(
            PhiloxState(
                key0 = key0,
                key1 = key1,
                counter0 = counter0,
                counter1 = counter1,
                counter2 = counter2,
                counter3 = counter3,
                block0 = block0,
                block1 = block1,
                block2 = block2,
                block3 = block3,
                blockIndex = blockIndex,
                hasUInt32 = hasUInt32,
                uinteger = uinteger,
            ),
        ),
    )

    override fun nextBits(bitCount: Int): Int = randomBitsFromInt(next32(), bitCount)

    override fun nextInt(): Int = next32()

    override fun nextLong(): Long = next64()

    override fun nextBoolean(): Boolean = next32() < 0

    override fun nextFloat(): Float = randomFloatFromInt(next32())

    override fun nextDouble(): Double = randomDoubleFromLong(next64())

    override fun nextBytes(array: ByteArray, fromIndex: Int, toIndex: Int): ByteArray =
        fillBytesFromLongs(array, fromIndex, toIndex, ::next64)

    fun next64(): Long {
        clearCachedUInt32()
        if (blockIndex == WORDS_PER_BLOCK) {
            refill()
        }

        val value =
            bufferedWordAt(blockIndex)
        blockIndex += 1
        return value
    }

    fun next32(): Int {
        if (hasUInt32) {
            hasUInt32 = false
            val value = uinteger
            uinteger = 0
            return value
        }

        val word = next64Word()
        hasUInt32 = true
        uinteger = (word ushr 32).toInt()
        return word.toInt()
    }

    /**
     * Returns the next 4x64 Philox block. Any buffered words or cached 32-bit half are discarded.
     */
    fun nextBlock(): LongArray {
        val block = block(counter0, counter1, counter2, counter3, key0, key1)
        clearBufferedState()
        incrementCounterWords(counter0, counter1, counter2, counter3) { next0, next1, next2, next3 ->
            counter0 = next0
            counter1 = next1
            counter2 = next2
            counter3 = next3
        }
        return block
    }

    /**
     * Advances the stored exact 256-bit counter by [delta] blocks and clears any buffered output.
     */
    fun advance(delta: ULong) {
        addToLowWord(counter0, counter1, counter2, counter3, delta) { next0, next1, next2, next3 ->
            counter0 = next0
            counter1 = next1
            counter2 = next2
            counter3 = next3
        }
        clearBufferedState()
    }

    fun advance(delta: Long) {
        require(delta >= 0L) { BAD_SIGNED_DELTA }
        advance(delta.toULong())
    }

    /**
     * Returns a generator with its counter advanced by [jumps] * 2^128 blocks. Buffered output is
     * discarded in the returned state.
     */
    fun jumped(): PhiloxRandom = jumped(1uL)

    fun jumped(jumps: ULong): PhiloxRandom =
        PhiloxRandom(
            addToJumpWord(
                key0 = key0,
                key1 = key1,
                counter0 = counter0,
                counter1 = counter1,
                counter2 = counter2,
                counter3 = counter3,
                jumps = jumps,
            ),
        )

    fun jumped(jumps: Long): PhiloxRandom {
        require(jumps >= 0L) { BAD_SIGNED_JUMPS }
        return jumped(jumps.toULong())
    }

    fun asImmutable(): Immutable =
        Immutable(
            key0 = key0,
            key1 = key1,
            counter0 = counter0,
            counter1 = counter1,
            counter2 = counter2,
            counter3 = counter3,
            block0 = block0,
            block1 = block1,
            block2 = block2,
            block3 = block3,
            blockIndex = blockIndex,
            hasUInt32 = hasUInt32,
            uinteger = uinteger,
        )

    private fun currentState(): PhiloxState =
        PhiloxState(
            key0 = key0,
            key1 = key1,
            counter0 = counter0,
            counter1 = counter1,
            counter2 = counter2,
            counter3 = counter3,
            block0 = block0,
            block1 = block1,
            block2 = block2,
            block3 = block3,
            blockIndex = blockIndex,
            hasUInt32 = hasUInt32,
            uinteger = uinteger,
        )

    override fun equals(other: Any?): Boolean = other is PhiloxRandom && currentState() == other.currentState()

    override fun hashCode(): Int = currentState().hashCode()

    private fun refill() {
        blockWords(counter0, counter1, counter2, counter3, key0, key1) { word0, word1, word2, word3 ->
            block0 = word0
            block1 = word1
            block2 = word2
            block3 = word3
        }
        blockIndex = 0
        incrementCounterWords(counter0, counter1, counter2, counter3) { next0, next1, next2, next3 ->
            counter0 = next0
            counter1 = next1
            counter2 = next2
            counter3 = next3
        }
    }

    private fun next64Word(): Long {
        if (blockIndex == WORDS_PER_BLOCK) {
            refill()
        }

        val value =
            bufferedWordAt(blockIndex)
        blockIndex += 1
        return value
    }

    private fun clearCachedUInt32() {
        hasUInt32 = false
        uinteger = 0
    }

    private fun clearBufferedState() {
        block0 = 0L
        block1 = 0L
        block2 = 0L
        block3 = 0L
        blockIndex = WORDS_PER_BLOCK
        clearCachedUInt32()
    }

    @Serializable
    class Immutable internal constructor(
        val key0: Long,
        val key1: Long,
        val counter0: Long,
        val counter1: Long,
        val counter2: Long,
        val counter3: Long,
        val block0: Long,
        val block1: Long,
        val block2: Long,
        val block3: Long,
        val blockIndex: Int,
        val hasUInt32: Boolean,
        val uinteger: Int,
    ) {
        init {
            validateBufferedState(blockIndex, hasUInt32)
        }

        constructor(
            key0: Long,
            key1: Long,
            counter0: Long = 0L,
            counter1: Long = 0L,
            counter2: Long = 0L,
            counter3: Long = 0L,
        ) : this(
            key0 = key0,
            key1 = key1,
            counter0 = counter0,
            counter1 = counter1,
            counter2 = counter2,
            counter3 = counter3,
            block0 = 0L,
            block1 = 0L,
            block2 = 0L,
            block3 = 0L,
            blockIndex = WORDS_PER_BLOCK,
            hasUInt32 = false,
            uinteger = 0,
        )

        internal constructor(initial: PhiloxState) : this(
            key0 = initial.key0,
            key1 = initial.key1,
            counter0 = initial.counter0,
            counter1 = initial.counter1,
            counter2 = initial.counter2,
            counter3 = initial.counter3,
            block0 = initial.block0,
            block1 = initial.block1,
            block2 = initial.block2,
            block3 = initial.block3,
            blockIndex = initial.blockIndex,
            hasUInt32 = initial.hasUInt32,
            uinteger = initial.uinteger,
        )

        fun next64(): RandomResult<Immutable, Long> {
            val aligned =
                if (hasUInt32) {
                    copy(hasUInt32 = false, uinteger = 0)
                } else {
                    this
                }

            if (aligned.blockIndex < WORDS_PER_BLOCK) {
                val value =
                    selectBufferedWord(
                        aligned.blockIndex,
                        aligned.block0,
                        aligned.block1,
                        aligned.block2,
                        aligned.block3,
                    )
                return RandomResult(
                    value,
                    aligned.copy(blockIndex = aligned.blockIndex + 1),
                )
            }

            return blockWords(
                aligned.counter0,
                aligned.counter1,
                aligned.counter2,
                aligned.counter3,
                aligned.key0,
                aligned.key1,
            ) { word0, word1, word2, word3 ->
                incrementCounterWords(aligned.counter0, aligned.counter1, aligned.counter2, aligned.counter3) {
                        next0,
                        next1,
                        next2,
                        next3,
                    ->
                    RandomResult(
                        word0,
                        aligned.copy(
                            counter0 = next0,
                            counter1 = next1,
                            counter2 = next2,
                            counter3 = next3,
                            block0 = word0,
                            block1 = word1,
                            block2 = word2,
                            block3 = word3,
                            blockIndex = 1,
                        ),
                    )
                }
            }
        }

        fun next32(): RandomResult<Immutable, Int> {
            if (hasUInt32) {
                return RandomResult(
                    uinteger,
                    copy(hasUInt32 = false, uinteger = 0),
                )
            }

            val step = next64()
            return RandomResult(
                step.value.toInt(),
                step.generator.copy(
                    hasUInt32 = true,
                    uinteger = (step.value ushr 32).toInt(),
                ),
            )
        }

        /**
         * Returns the next 4x64 Philox block. Any buffered words or cached 32-bit half are
         * discarded in the returned continuation state.
         */
        fun nextBlock(): RandomResult<Immutable, LongArray> {
            return blockWords(counter0, counter1, counter2, counter3, key0, key1) { word0, word1, word2, word3 ->
                incrementCounterWords(counter0, counter1, counter2, counter3) { next0, next1, next2, next3 ->
                    RandomResult(
                        longArrayOf(word0, word1, word2, word3),
                        copy(
                            counter0 = next0,
                            counter1 = next1,
                            counter2 = next2,
                            counter3 = next3,
                            block0 = 0L,
                            block1 = 0L,
                            block2 = 0L,
                            block3 = 0L,
                            blockIndex = WORDS_PER_BLOCK,
                            hasUInt32 = false,
                            uinteger = 0,
                        ),
                    )
                }
            }
        }

        /**
         * Advances the stored exact 256-bit counter by [delta] blocks and clears any buffered
         * output.
         */
        fun advance(delta: ULong): Immutable =
            addToLowWord(counter0, counter1, counter2, counter3, delta) { next0, next1, next2, next3 ->
                    copy(
                        counter0 = next0,
                        counter1 = next1,
                        counter2 = next2,
                        counter3 = next3,
                        block0 = 0L,
                        block1 = 0L,
                        block2 = 0L,
                        block3 = 0L,
                        blockIndex = WORDS_PER_BLOCK,
                        hasUInt32 = false,
                        uinteger = 0,
                    )
                }

        fun advance(delta: Long): Immutable {
            require(delta >= 0L) { BAD_SIGNED_DELTA }
            return advance(delta.toULong())
        }

        /**
         * Returns a generator with its counter advanced by [jumps] * 2^128 blocks. Buffered output
         * is discarded in the returned state.
         */
        fun jumped(): Immutable = jumped(1uL)

        fun jumped(jumps: ULong): Immutable =
            Immutable(
                addToJumpWord(
                    counter0 = counter0,
                    counter1 = counter1,
                    counter2 = counter2,
                    counter3 = counter3,
                    jumps = jumps,
                    key0 = key0,
                    key1 = key1,
                ),
            )

        fun jumped(jumps: Long): Immutable {
            require(jumps >= 0L) { BAD_SIGNED_JUMPS }
            return jumped(jumps.toULong())
        }

        fun asMutable(): PhiloxRandom =
            PhiloxRandom(
                PhiloxState(
                    key0 = key0,
                    key1 = key1,
                    counter0 = counter0,
                    counter1 = counter1,
                    counter2 = counter2,
                    counter3 = counter3,
                    block0 = block0,
                    block1 = block1,
                    block2 = block2,
                    block3 = block3,
                    blockIndex = blockIndex,
                    hasUInt32 = hasUInt32,
                    uinteger = uinteger,
                ),
            )

        private fun state(): PhiloxState =
            PhiloxState(
                key0 = key0,
                key1 = key1,
                counter0 = counter0,
                counter1 = counter1,
                counter2 = counter2,
                counter3 = counter3,
                block0 = block0,
                block1 = block1,
                block2 = block2,
                block3 = block3,
                blockIndex = blockIndex,
                hasUInt32 = hasUInt32,
                uinteger = uinteger,
            )

        override fun equals(other: Any?): Boolean = other is Immutable && state() == other.state()

        override fun hashCode(): Int = state().hashCode()

        private fun copy(
            key0: Long = this.key0,
            key1: Long = this.key1,
            counter0: Long = this.counter0,
            counter1: Long = this.counter1,
            counter2: Long = this.counter2,
            counter3: Long = this.counter3,
            block0: Long = this.block0,
            block1: Long = this.block1,
            block2: Long = this.block2,
            block3: Long = this.block3,
            blockIndex: Int = this.blockIndex,
            hasUInt32: Boolean = this.hasUInt32,
            uinteger: Int = this.uinteger,
        ): Immutable =
            Immutable(
                key0 = key0,
                key1 = key1,
                counter0 = counter0,
                counter1 = counter1,
                counter2 = counter2,
                counter3 = counter3,
                block0 = block0,
                block1 = block1,
                block2 = block2,
                block3 = block3,
                blockIndex = blockIndex,
                hasUInt32 = hasUInt32,
                uinteger = uinteger,
            )
    }

    companion object {
        private const val WORDS_PER_BLOCK = PHILOX_WORDS_PER_BLOCK
        private const val ROUND_COUNT = 10
        private const val MULTIPLIER_0 = -3249550476889527149L
        private const val MULTIPLIER_1 = -3865633965929787049L
        private const val WEYL_1 = -4942790177534073029L
        private const val LOW_MASK = 0xffffffffuL
        private const val BAD_BLOCK_INDEX =
            "Philox blockIndex must be between 0 and $WORDS_PER_BLOCK inclusive"
        private const val BAD_UINT32_CACHE =
            "Philox hasUInt32 requires blockIndex to be between 1 and $WORDS_PER_BLOCK inclusive"
        private const val BAD_SIGNED_DELTA = "delta must be non-negative"
        private const val BAD_SIGNED_JUMPS = "jumps must be non-negative"

        /**
         * Applies the raw Philox bijection to the exact counter value supplied.
         */
        fun block(
            counter0: Long,
            counter1: Long,
            counter2: Long,
            counter3: Long,
            key0: Long,
            key1: Long,
        ): LongArray =
            blockWords(counter0, counter1, counter2, counter3, key0, key1) { word0, word1, word2, word3 ->
                longArrayOf(word0, word1, word2, word3)
            }

        // Keep the Philox round pipeline in locals so Native/JS do not pay for tiny helper objects.
        private inline fun <T> blockWords(
            counter0: Long,
            counter1: Long,
            counter2: Long,
            counter3: Long,
            key0: Long,
            key1: Long,
            consume: (Long, Long, Long, Long) -> T,
        ): T {
            var c0 = counter0
            var c1 = counter1
            var c2 = counter2
            var c3 = counter3
            var k0 = key0
            var k1 = key1

            repeat(ROUND_COUNT) {
                var product0High = 0L
                var product0Low = 0L
                multiplyTo128(MULTIPLIER_0, c0) { high, low ->
                    product0High = high
                    product0Low = low
                }

                var product1High = 0L
                var product1Low = 0L
                multiplyTo128(MULTIPLIER_1, c2) { high, low ->
                    product1High = high
                    product1Low = low
                }

                val next0 = product1High xor c1 xor k0
                val next1 = product1Low
                val next2 = product0High xor c3 xor k1
                val next3 = product0Low

                c0 = next0
                c1 = next1
                c2 = next2
                c3 = next3
                k0 += WEYL_INCREMENT_64
                k1 += WEYL_1
            }

            return consume(c0, c1, c2, c3)
        }

        private inline fun <T> incrementCounterWords(
            counter0: Long,
            counter1: Long,
            counter2: Long,
            counter3: Long,
            consume: (Long, Long, Long, Long) -> T,
        ): T = addToLowWord(counter0, counter1, counter2, counter3, 1uL, consume)

        private fun validateState(state: PhiloxState): PhiloxState {
            validateBufferedState(state.blockIndex, state.hasUInt32)
            return state
        }

        private fun validateBufferedState(blockIndex: Int, hasUInt32: Boolean) {
            require(blockIndex in 0..WORDS_PER_BLOCK) { BAD_BLOCK_INDEX }
            require(!hasUInt32 || blockIndex in 1..WORDS_PER_BLOCK) { BAD_UINT32_CACHE }
        }

        private inline fun <T> addToLowWord(
            counter0: Long,
            counter1: Long,
            counter2: Long,
            counter3: Long,
            delta: ULong,
            consume: (Long, Long, Long, Long) -> T,
        ): T {
            val sum0 = counter0.toULong() + delta
            val carry0 = if (sum0 < counter0.toULong()) 1uL else 0uL

            val sum1 = counter1.toULong() + carry0
            val carry1 = if (carry0 != 0uL && sum1 == 0uL) 1uL else 0uL

            val sum2 = counter2.toULong() + carry1
            val carry2 = if (carry1 != 0uL && sum2 == 0uL) 1uL else 0uL

            val sum3 = counter3.toULong() + carry2

            return consume(sum0.toLong(), sum1.toLong(), sum2.toLong(), sum3.toLong())
        }

        private fun addToJumpWord(
            counter0: Long,
            counter1: Long,
            counter2: Long,
            counter3: Long,
            jumps: ULong,
            key0: Long = 0L,
            key1: Long = 0L,
        ): PhiloxState {
            val sum2 = counter2.toULong() + jumps
            val carry2 = if (sum2 < counter2.toULong()) 1uL else 0uL
            val sum3 = counter3.toULong() + carry2

            return PhiloxState(
                key0 = key0,
                key1 = key1,
                counter0 = counter0,
                counter1 = counter1,
                counter2 = sum2.toLong(),
                counter3 = sum3.toLong(),
            )
        }

        private inline fun multiplyTo128(
            left: Long,
            right: Long,
            consume: (high: Long, low: Long) -> Unit,
        ) {
            val a = left.toULong()
            val b = right.toULong()

            val a0 = a and LOW_MASK
            val a1 = a shr 32
            val b0 = b and LOW_MASK
            val b1 = b shr 32

            val p00 = a0 * b0
            val p01 = a0 * b1
            val p10 = a1 * b0
            val p11 = a1 * b1

            val middle = (p00 shr 32) + (p01 and LOW_MASK) + (p10 and LOW_MASK)
            val high = p11 + (p01 shr 32) + (p10 shr 32) + (middle shr 32)
            val low = (middle shl 32) or (p00 and LOW_MASK)

            consume(high.toLong(), low.toLong())
        }

        private fun selectBufferedWord(
            index: Int,
            block0: Long,
            block1: Long,
            block2: Long,
            block3: Long,
        ): Long =
            when (index) {
                0 -> block0
                1 -> block1
                2 -> block2
                3 -> block3
                else -> error("Invalid Philox blockIndex: $index")
            }
    }

    private fun bufferedWordAt(index: Int): Long = selectBufferedWord(index, block0, block1, block2, block3)
}

internal data class PhiloxState(
    val key0: Long,
    val key1: Long,
    val counter0: Long,
    val counter1: Long,
    val counter2: Long,
    val counter3: Long,
    val block0: Long = 0L,
    val block1: Long = 0L,
    val block2: Long = 0L,
    val block3: Long = 0L,
    val blockIndex: Int = PHILOX_WORDS_PER_BLOCK,
    val hasUInt32: Boolean = false,
    val uinteger: Int = 0,
)
