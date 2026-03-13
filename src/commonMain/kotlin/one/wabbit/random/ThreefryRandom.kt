package one.wabbit.random

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Mutable Threefry 2x32 generator with JAX-style key derivation helpers.
 *
 * The sequential generator hashes the current 64-bit counter into a 2x32 block and then
 * increments it by one block. `next64()` is block-atomic: it discards a partially consumed
 * 32-bit buffer so the returned value always comes from a single counter. `split` and `foldIn`
 * operate on the key only, matching JAX's functional PRNG model. Full restore constructors that
 * include buffered words are internal; use `asImmutable()` / `asMutable()` to round-trip those
 * opaque snapshots. The higher-level `Random` primitives follow the same alignment rules, so
 * `nextDouble()` and `nextBytes(...)` are also block-atomic.
 */
class ThreefryRandom private constructor(initial: ThreefryState) : Random() {
    val key0: Int = initial.key0
    val key1: Int = initial.key1
    var counter: Long = initial.counter
        private set

    private var block0: Int = initial.block0
    private var block1: Int = initial.block1
    private var blockIndex: Int = initial.blockIndex

    constructor(
        key0: Int,
        key1: Int,
        counter: Long = 0L,
    ) : this(validateState(ThreefryState(key0, key1, counter)))

    internal constructor(
        key0: Int,
        key1: Int,
        counter: Long,
        block0: Int,
        block1: Int,
        blockIndex: Int,
    ) : this(validateState(ThreefryState(key0, key1, counter, block0, block1, blockIndex)))

    constructor(seed: Long) : this(seedToKey0(seed), seedToKey1(seed), 0L)

    override fun nextBits(bitCount: Int): Int = randomBitsFromInt(next32(), bitCount)

    override fun nextInt(): Int = next32()

    override fun nextLong(): Long = next64()

    override fun nextBoolean(): Boolean = next32() < 0

    override fun nextFloat(): Float = randomFloatFromInt(next32())

    override fun nextDouble(): Double = randomDoubleFromLong(next64())

    override fun nextBytes(array: ByteArray, fromIndex: Int, toIndex: Int): ByteArray =
        fillBytesFromLongs(array, fromIndex, toIndex, ::next64)

    fun next32(): Int {
        if (blockIndex == WORDS_PER_BLOCK) {
            refill()
        }

        val value = bufferedWordAt(blockIndex)
        blockIndex += 1
        return value
    }

    fun next64(): Long {
        return when (blockIndex) {
            0 -> {
                val value = combineToLong(block0, block1)
                clearBlock()
                value
            }
            1 -> {
                // The discarded tail belongs to the already-buffered block whose counter was
                // consumed during `refill()`, so hashing `counter` here does not skip a block.
                clearBlock()
                hashCounterWords(key0, key1, counter) { left, right ->
                    counter += 1L
                    combineToLong(left, right)
                }
            }
            WORDS_PER_BLOCK ->
                hashCounterWords(key0, key1, counter) { left, right ->
                    counter += 1L
                    combineToLong(left, right)
                }
            else -> error("Invalid Threefry blockIndex: $blockIndex")
        }
    }

    /**
     * Returns the next 2x32 Threefry block. Any buffered 32-bit values are discarded.
     */
    fun nextBlock(): IntArray {
        return hashCounterWords(key0, key1, counter) { left, right ->
            counter += 1L
            clearBlock()
            intArrayOf(left, right)
        }
    }

    fun split(count: Int = 2): List<ThreefryRandom> {
        require(count >= 0) { BAD_SPLIT }

        return List(count) { index ->
            hashWords(key0, key1, 0, index) { left, right ->
                ThreefryRandom(left, right)
            }
        }
    }

    fun split2(): Pair<ThreefryRandom, ThreefryRandom> {
        val children = split(2)
        return children[0] to children[1]
    }

    fun foldIn(data: Int): ThreefryRandom {
        return hashWords(key0, key1, 0, data) { left, right ->
            ThreefryRandom(left, right)
        }
    }

    fun asImmutable(): Immutable = Immutable(key0, key1, counter, block0, block1, blockIndex)

    private fun currentState(): ThreefryState =
        ThreefryState(
            key0 = key0,
            key1 = key1,
            counter = counter,
            block0 = block0,
            block1 = block1,
            blockIndex = blockIndex,
        )

    override fun equals(other: Any?): Boolean = other is ThreefryRandom && currentState() == other.currentState()

    override fun hashCode(): Int = currentState().hashCode()

    private fun refill() {
        hashCounterWords(key0, key1, counter) { left, right ->
            counter += 1L
            block0 = left
            block1 = right
            blockIndex = 0
        }
    }

    private fun clearBlock() {
        block0 = 0
        block1 = 0
        blockIndex = WORDS_PER_BLOCK
    }

    @Serializable
    class Immutable internal constructor(
        val key0: Int,
        val key1: Int,
        val counter: Long,
        val block0: Int,
        val block1: Int,
        val blockIndex: Int,
    ) {
        init {
            validateBlockIndex(blockIndex)
        }

        constructor(
            key0: Int,
            key1: Int,
            counter: Long = 0L,
        ) : this(
            key0 = key0,
            key1 = key1,
            counter = counter,
            block0 = 0,
            block1 = 0,
            blockIndex = WORDS_PER_BLOCK,
        )

        internal constructor(initial: ThreefryState) : this(
            key0 = initial.key0,
            key1 = initial.key1,
            counter = initial.counter,
            block0 = initial.block0,
            block1 = initial.block1,
            blockIndex = initial.blockIndex,
        )

        constructor(seed: Long) : this(seedToKey0(seed), seedToKey1(seed), 0L)

        fun next32(): RandomResult<Immutable, Int> {
            return when (blockIndex) {
                0, 1 -> {
                    val value = bufferedWordAt(blockIndex, block0, block1)
                    RandomResult(value, copy(blockIndex = blockIndex + 1))
                }
                WORDS_PER_BLOCK ->
                    hashCounterWords(key0, key1, counter) { left, right ->
                        RandomResult(
                            left,
                            copy(
                                counter = counter + 1L,
                                block0 = left,
                                block1 = right,
                                blockIndex = 1,
                            ),
                        )
                    }
                else -> error("Invalid Threefry blockIndex: $blockIndex")
            }
        }

        fun next64(): RandomResult<Immutable, Long> {
            return when (blockIndex) {
                0 ->
                    RandomResult(
                        combineToLong(block0, block1),
                        copy(block0 = 0, block1 = 0, blockIndex = WORDS_PER_BLOCK),
                    )
                1, WORDS_PER_BLOCK -> {
                    val aligned =
                        if (blockIndex == 1) {
                            copy(block0 = 0, block1 = 0, blockIndex = WORDS_PER_BLOCK)
                        } else {
                            this
                        }
                    hashCounterWords(aligned.key0, aligned.key1, aligned.counter) { left, right ->
                        RandomResult(
                            combineToLong(left, right),
                            aligned.copy(
                                counter = aligned.counter + 1L,
                                block0 = 0,
                                block1 = 0,
                                blockIndex = WORDS_PER_BLOCK,
                            ),
                        )
                    }
                }
                else -> error("Invalid Threefry blockIndex: $blockIndex")
            }
        }

        /**
         * Returns the next 2x32 Threefry block. Any buffered 32-bit values are discarded in the
         * returned continuation state.
         */
        fun nextBlock(): RandomResult<Immutable, IntArray> {
            return hashCounterWords(key0, key1, counter) { left, right ->
                RandomResult(
                    intArrayOf(left, right),
                    copy(
                        counter = counter + 1L,
                        block0 = 0,
                        block1 = 0,
                        blockIndex = WORDS_PER_BLOCK,
                    ),
                )
            }
        }

        fun split(count: Int = 2): List<Immutable> {
            require(count >= 0) { BAD_SPLIT }

            return List(count) { index ->
                hashWords(key0, key1, 0, index) { left, right ->
                    Immutable(left, right)
                }
            }
        }

        fun split2(): Pair<Immutable, Immutable> {
            val children = split(2)
            return children[0] to children[1]
        }

        fun foldIn(data: Int): Immutable {
            return hashWords(key0, key1, 0, data) { left, right ->
                Immutable(left, right)
            }
        }

        fun asMutable(): ThreefryRandom = ThreefryRandom(key0, key1, counter, block0, block1, blockIndex)

        private fun state(): ThreefryState =
            ThreefryState(
                key0 = key0,
                key1 = key1,
                counter = counter,
                block0 = block0,
                block1 = block1,
                blockIndex = blockIndex,
            )

        override fun equals(other: Any?): Boolean = other is Immutable && state() == other.state()

        override fun hashCode(): Int = state().hashCode()

        private fun copy(
            key0: Int = this.key0,
            key1: Int = this.key1,
            counter: Long = this.counter,
            block0: Int = this.block0,
            block1: Int = this.block1,
            blockIndex: Int = this.blockIndex,
        ): Immutable = Immutable(key0, key1, counter, block0, block1, blockIndex)
    }

    companion object {
        private const val WORDS_PER_BLOCK = 2
        private const val BAD_SPLIT = "split count must be non-negative"
        private const val BAD_BLOCK_INDEX =
            "Threefry blockIndex must be between 0 and $WORDS_PER_BLOCK inclusive"
        private const val PARITY = 0x1BD11BDA
        private val ROTATIONS_0 = intArrayOf(13, 15, 26, 6)
        private val ROTATIONS_1 = intArrayOf(17, 29, 16, 24)

        fun hash(key0: Int, key1: Int, count0: Int, count1: Int): IntArray =
            hashWords(key0, key1, count0, count1) { left, right ->
                intArrayOf(left, right)
            }

        // Keep the Threefry mix schedule in locals so block hashing stays allocation-free.
        private inline fun <T> hashWords(
            key0: Int,
            key1: Int,
            count0: Int,
            count1: Int,
            consume: (Int, Int) -> T,
        ): T {
            val parity = key0 xor key1 xor PARITY

            var x0 = count0 + key0
            var x1 = count1 + key1

            for (rotation in ROTATIONS_0) {
                x0 += x1
                x1 = rotateLeft32(x1, rotation) xor x0
            }
            x0 += key1
            x1 += parity + 1

            for (rotation in ROTATIONS_1) {
                x0 += x1
                x1 = rotateLeft32(x1, rotation) xor x0
            }
            x0 += parity
            x1 += key0 + 2

            for (rotation in ROTATIONS_0) {
                x0 += x1
                x1 = rotateLeft32(x1, rotation) xor x0
            }
            x0 += key0
            x1 += key1 + 3

            for (rotation in ROTATIONS_1) {
                x0 += x1
                x1 = rotateLeft32(x1, rotation) xor x0
            }
            x0 += key1
            x1 += parity + 4

            for (rotation in ROTATIONS_0) {
                x0 += x1
                x1 = rotateLeft32(x1, rotation) xor x0
            }
            x0 += parity
            x1 += key0 + 5

            return consume(x0, x1)
        }

        fun seed(seed: Long): ThreefryRandom = ThreefryRandom(seed)

        private fun validateState(state: ThreefryState): ThreefryState {
            validateBlockIndex(state.blockIndex)
            return state
        }

        private fun validateBlockIndex(blockIndex: Int) {
            require(blockIndex in 0..WORDS_PER_BLOCK) { BAD_BLOCK_INDEX }
        }

        private inline fun <T> hashCounterWords(
            key0: Int,
            key1: Int,
            counter: Long,
            consume: (Int, Int) -> T,
        ): T =
            hashWords(
                key0 = key0,
                key1 = key1,
                count0 = (counter ushr 32).toInt(),
                count1 = counter.toInt(),
                consume = consume,
            )

        private fun combineToLong(high: Int, low: Int): Long =
            (high.toLong() shl 32) or (low.toLong() and 0xffffffffL)

        private fun seedToKey0(seed: Long): Int = (seed ushr 32).toInt()

        private fun seedToKey1(seed: Long): Int = seed.toInt()

        private fun bufferedWordAt(index: Int, block0: Int, block1: Int): Int =
            when (index) {
                0 -> block0
                1 -> block1
                else -> error("Invalid Threefry blockIndex: $index")
            }
    }

    private fun bufferedWordAt(index: Int): Int = bufferedWordAt(index, block0, block1)
}

internal data class ThreefryState(
    val key0: Int,
    val key1: Int,
    val counter: Long,
    val block0: Int = 0,
    val block1: Int = 0,
    val blockIndex: Int = 2,
)
