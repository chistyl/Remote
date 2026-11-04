package com.chi.tvremote.protocol

import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceResolveEpochTest {
    @Test
    fun oldResolveRemainsStaleAfterSameServiceIsRediscovered() {
        val epochs = ServiceResolveEpoch()
        val oldResolveEpoch = epochs.snapshot("LivingRoom-TV")

        epochs.markLost("LivingRoom-TV")
        // A new onServiceFound removes the UI lost marker, but must not
        // make the already-running old resolve callback valid again.
        assertTrue(!epochs.isCurrent("LivingRoom-TV", oldResolveEpoch))

        val newResolveEpoch = epochs.snapshot("LivingRoom-TV")
        assertTrue(epochs.isCurrent("LivingRoom-TV", newResolveEpoch))
    }

    @Test
    fun lossOfOneServiceDoesNotInvalidateAnotherService() {
        val epochs = ServiceResolveEpoch()
        val tvA = epochs.snapshot("TV-A")
        val tvB = epochs.snapshot("TV-B")

        epochs.markLost("TV-A")

        assertTrue(!epochs.isCurrent("TV-A", tvA))
        assertTrue(epochs.isCurrent("TV-B", tvB))
    }

    @Test
    fun clearingForNewDiscoveryResetsServiceEpochs() {
        val epochs = ServiceResolveEpoch()
        epochs.markLost("TV-A")
        epochs.clear()

        assertTrue(epochs.isCurrent("TV-A", 0L))
    }
}
