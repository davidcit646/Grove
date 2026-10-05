package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class ReportPromptPolicyTest {
    @Test fun automaticPromptRequiresEnabledCaptureAndPendingReport() {
        assertTrue(ReportPromptPolicy.shouldPrompt(1, true, false, false))
        assertFalse(ReportPromptPolicy.shouldPrompt(0, true, false, false))
        assertFalse(ReportPromptPolicy.shouldPrompt(1, false, false, false))
    }

    @Test fun explicitReviewWorksWhenAutomaticCaptureIsDisabled() {
        assertTrue(ReportPromptPolicy.shouldPrompt(1, false, false, true))
    }

    @Test fun repeatedPromptIsSuppressedWhileOneIsVisible() {
        assertFalse(ReportPromptPolicy.shouldPrompt(1, true, true, false))
        assertFalse(ReportPromptPolicy.shouldPrompt(1, false, true, true))
    }
    @Test fun mailHandoffFailsClosedWithoutAHandler() {
        assertFalse(ReportHandoffPolicy.hasMailHandler(0))
        assertTrue(ReportHandoffPolicy.hasMailHandler(1))
        assertTrue(ReportHandoffPolicy.hasMailHandler(3))
    }

}
