// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.random

import kotlinx.serialization.Serializable

/**
 * Result of a pure immutable random-generator step.
 *
 * Immutable generators cannot mutate themselves, so methods return both the sampled [value] and the
 * next [generator] state that should be used for subsequent samples.
 *
 * @property value the random value produced by the step.
 * @property generator the continuation generator state.
 */
@Serializable data class RandomResult<Rng, Value>(val value: Value, val generator: Rng)
