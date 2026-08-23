package com.github.lutzluca.btrbz.core.bazaariteminfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TemporaryScreenTransitionTest {
    @Test
    void completionResumesWithoutAbandoningTheOwner() {
        var abandonments = new AtomicInteger();
        var transition = new TemporaryScreenTransition(abandonments::incrementAndGet);

        assertTrue(transition.suppressOwnerRemoval());
        assertTrue(transition.complete());
        transition.abandon();

        assertFalse(transition.suppressOwnerRemoval());
        assertFalse(transition.complete());
        assertEquals(0, abandonments.get());
    }

    @Test
    void abandonmentDisposesOnlyOnceAndCannotLaterComplete() {
        var abandonments = new AtomicInteger();
        var transition = new TemporaryScreenTransition(abandonments::incrementAndGet);

        transition.abandon();
        transition.abandon();

        assertFalse(transition.suppressOwnerRemoval());
        assertFalse(transition.complete());
        assertEquals(1, abandonments.get());
    }
}
