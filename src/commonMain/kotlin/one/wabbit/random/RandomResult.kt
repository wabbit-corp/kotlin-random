package one.wabbit.random

import kotlinx.serialization.Serializable

@Serializable
data class RandomResult<Rng, Value>(
    val value: Value,
    val generator: Rng,
)
