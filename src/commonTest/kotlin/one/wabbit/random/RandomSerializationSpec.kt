package one.wabbit.random

import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RandomSerializationSpec {
    private val json = Json { encodeDefaults = true }

    @Test
    fun random_result_round_trips() {
        val original = L64X128Random.Immutable(0x1234).next64()
        val restored = roundTrip(original)

        assertEquals(original, restored)
        assertEquals(original.hashCode(), restored.hashCode())
    }

    @Test
    fun l64x128_snapshot_round_trips() {
        val original = L64X128Random.Immutable(0x1234).next64().generator
        val restored = roundTrip(original)

        assertEquals(original, restored)
        assertSameRandomStream(original.asMutable(), restored.asMutable())
    }

    @Test
    fun philox_snapshot_round_trips_with_buffered_state() {
        val original = PhiloxRandom.Immutable(key0 = 0x1234, key1 = 0x5678, counter0 = 1L).next32().generator
        val restored = roundTrip(original)

        assertEquals(original, restored)
        assertSameRandomStream(original.asMutable(), restored.asMutable())
    }

    @Test
    fun threefry_snapshot_round_trips_with_buffered_state() {
        val original = ThreefryRandom.Immutable(0x1234).next32().generator
        val restored = roundTrip(original)

        assertEquals(original, restored)
        assertSameRandomStream(original.asMutable(), restored.asMutable())
    }

    @Test
    fun xoshiro_plus_plus_snapshot_round_trips() {
        val original = Xoshiro256PlusPlusRandom.Immutable(0x1234).next64().generator
        val restored = roundTrip(original)

        assertEquals(original, restored)
        assertSameRandomStream(original.asMutable(), restored.asMutable())
    }

    @Test
    fun xoshiro_star_star_snapshot_round_trips() {
        val original = Xoshiro256StarStarRandom.Immutable(0x1234).next64().generator
        val restored = roundTrip(original)

        assertEquals(original, restored)
        assertSameRandomStream(original.asMutable(), restored.asMutable())
    }

    private inline fun <reified T> roundTrip(value: T): T {
        val encoded = json.encodeToString(value)
        return json.decodeFromString(encoded)
    }

    private fun assertSameRandomStream(left: Random, right: Random) {
        repeat(4) {
            assertEquals(left.nextInt(), right.nextInt())
            assertEquals(left.nextLong(), right.nextLong())
        }

        val leftBytes = ByteArray(19)
        val rightBytes = ByteArray(19)
        left.nextBytes(leftBytes)
        right.nextBytes(rightBytes)
        assertContentEquals(leftBytes, rightBytes)

        repeat(4) {
            assertEquals(left.nextBoolean(), right.nextBoolean())
            assertEquals(left.nextFloat(), right.nextFloat())
            assertEquals(left.nextDouble(), right.nextDouble())
        }
    }
}
