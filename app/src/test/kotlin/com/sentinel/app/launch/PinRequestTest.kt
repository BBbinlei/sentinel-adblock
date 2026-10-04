package com.sentinel.app.launch

import org.junit.Test
import kotlin.test.*

class PinRequestTest {
    @Test fun submissionIsNotConfirmationAndCancellationIsNotClaimedAsSuccess() {
        assertEquals(PinFeedback.REQUESTED, pinFeedback(true, true, false))
        assertEquals(PinFeedback.CONFIRMED, pinFeedback(true, true, true))
        assertEquals(PinFeedback.UNSUPPORTED, pinFeedback(false, false, false))
        assertEquals(PinFeedback.REJECTED, pinFeedback(true, false, false))
        assertEquals(PinFeedback.UNCONFIRMED, pinFeedback(true, true, false, returning = true))
    }
}
