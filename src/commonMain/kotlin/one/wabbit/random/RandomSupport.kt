package one.wabbit.random

private const val BAD_BIT_COUNT = "bitCount must be in 0..32"
private const val FLOAT_PRECISION = 24
private const val DOUBLE_PRECISION = 53
internal const val WEYL_INCREMENT_64 = -7046029254386353131L

// Exact IEEE-754 encodings of 2^-24 and 2^-53.
private val FLOAT_UNIT = Float.fromBits(0x33800000)
private val DOUBLE_UNIT = Double.fromBits(0x3CA0000000000000L)

internal fun randomBitsFromInt(value: Int, bitCount: Int): Int {
    require(bitCount >= 0 && bitCount <= Int.SIZE_BITS) { BAD_BIT_COUNT }

    return when (bitCount) {
        0 -> 0
        Int.SIZE_BITS -> value
        else -> value ushr (Int.SIZE_BITS - bitCount)
    }
}

internal fun randomFloatFromInt(value: Int): Float =
    (value ushr (Int.SIZE_BITS - FLOAT_PRECISION)).toFloat() * FLOAT_UNIT

internal fun randomDoubleFromLong(value: Long): Double =
    (value ushr (Long.SIZE_BITS - DOUBLE_PRECISION)).toDouble() * DOUBLE_UNIT

internal fun rotateLeft32(value: Int, distance: Int): Int =
    (value shl distance) or (value ushr (Int.SIZE_BITS - distance))

internal fun rotateLeft64(value: Long, distance: Int): Long =
    (value shl distance) or (value ushr (Long.SIZE_BITS - distance))

internal inline fun fillBytesFromLongs(
    array: ByteArray,
    fromIndex: Int,
    toIndex: Int,
    nextWord: () -> Long,
): ByteArray {
    require(fromIndex in 0..array.size && toIndex in 0..array.size) {
        "fromIndex ($fromIndex) or toIndex ($toIndex) are out of range: 0..${array.size}."
    }
    require(fromIndex <= toIndex) {
        "fromIndex ($fromIndex) must be not greater than toIndex ($toIndex)."
    }

    var position = fromIndex
    val wordCount = (toIndex - fromIndex) / Long.SIZE_BYTES

    repeat(wordCount) {
        var word = nextWord()
        repeat(Long.SIZE_BYTES) {
            array[position] = word.toByte()
            position += 1
            word = word ushr Byte.SIZE_BITS
        }
    }

    if (position < toIndex) {
        var word = nextWord()
        while (position < toIndex) {
            array[position] = word.toByte()
            position += 1
            word = word ushr Byte.SIZE_BITS
        }
    }

    return array
}
