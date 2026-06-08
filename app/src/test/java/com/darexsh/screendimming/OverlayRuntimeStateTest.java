package com.darexsh.screendimming;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OverlayRuntimeStateTest {

    @Test
    public void overlayUpdate_isSkippedWhenColorWouldStayTheSame() {
        OverlayRuntimeState state = new OverlayRuntimeState();

        assertTrue(state.recordOverlay(40, OverlayPrefs.FILTER_BLACK));
        assertFalse(state.recordOverlay(40, OverlayPrefs.FILTER_BLACK));
    }

    @Test
    public void overlayUpdate_runsWhenIntensityOrFilterChanges() {
        OverlayRuntimeState state = new OverlayRuntimeState();

        state.recordOverlay(40, OverlayPrefs.FILTER_BLACK);

        assertTrue(state.recordOverlay(45, OverlayPrefs.FILTER_BLACK));
        assertTrue(state.recordOverlay(45, OverlayPrefs.FILTER_WARM));
    }

    @Test
    public void notificationUpdate_isSkippedForRepeatedIntensity() {
        OverlayRuntimeState state = new OverlayRuntimeState();

        assertTrue(state.recordNotificationIntensity(55));
        assertFalse(state.recordNotificationIntensity(55));
        assertTrue(state.recordNotificationIntensity(60));
    }

    @Test
    public void broadcast_isSkippedWhenStateDidNotChange() {
        OverlayRuntimeState state = new OverlayRuntimeState();

        assertTrue(state.recordBroadcast(true, 70));
        assertFalse(state.recordBroadcast(true, 70));
        assertTrue(state.recordBroadcast(true, 75));
        assertTrue(state.recordBroadcast(false, 75));
    }

    @Test
    public void reset_clearsCachedState() {
        OverlayRuntimeState state = new OverlayRuntimeState();

        state.recordOverlay(50, OverlayPrefs.FILTER_BLUE);
        state.recordNotificationIntensity(50);
        state.recordBroadcast(true, 50);
        state.markForegroundStarted();

        state.reset();

        assertTrue(state.recordOverlay(50, OverlayPrefs.FILTER_BLUE));
        assertTrue(state.recordNotificationIntensity(50));
        assertTrue(state.recordBroadcast(true, 50));
        assertFalse(state.isForegroundStarted());
    }

    @Test
    public void foregroundStart_isOnlyNeededOnceUntilReset() {
        OverlayRuntimeState state = new OverlayRuntimeState();

        assertFalse(state.isForegroundStarted());
        state.markForegroundStarted();
        assertTrue(state.isForegroundStarted());

        state.reset();

        assertEquals(false, state.isForegroundStarted());
    }
}
