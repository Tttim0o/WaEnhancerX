package com.waenhancer.xposed.features.media;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ProximityWakeLockTest {

    @Test
    public void proximityScreenOffLockBecomesPartial() {
        assertEquals(ProximityEventPolicy.PARTIAL_WAKE_LOCK,
                ProximityEventPolicy.withoutProximityScreenOff(ProximityEventPolicy.PROXIMITY_SCREEN_OFF_WAKE_LOCK));
        // With ACQUIRE_CAUSES_WAKEUP (0x10000000) set, the flag is dropped too.
        assertEquals(ProximityEventPolicy.PARTIAL_WAKE_LOCK,
                ProximityEventPolicy.withoutProximityScreenOff(0x10000000 | 32));
    }

    @Test
    public void otherLocksAreUntouched() {
        assertEquals(1, ProximityEventPolicy.withoutProximityScreenOff(1));          // partial
        assertEquals(26, ProximityEventPolicy.withoutProximityScreenOff(26));        // full
        assertEquals(0x1000000a, ProximityEventPolicy.withoutProximityScreenOff(0x1000000a)); // bright + wakeup
    }
}
