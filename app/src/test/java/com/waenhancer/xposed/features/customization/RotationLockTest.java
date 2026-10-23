package com.waenhancer.xposed.features.customization;

import static org.junit.Assert.assertEquals;

import android.content.pm.ActivityInfo;
import org.junit.Test;

public class RotationLockTest {

    @Test
    public void sensorOrientationsBecomeLockRespectingOnes() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER,
                RotationLockHook.respectingRotationLock(ActivityInfo.SCREEN_ORIENTATION_SENSOR));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
                RotationLockHook.respectingRotationLock(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
                RotationLockHook.respectingRotationLock(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE));
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
                RotationLockHook.respectingRotationLock(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT));
    }

    @Test
    public void fixedAndUserOrientationsAreUntouched() {
        int[] unchanged = {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, ActivityInfo.SCREEN_ORIENTATION_USER,
                ActivityInfo.SCREEN_ORIENTATION_LOCKED, ActivityInfo.SCREEN_ORIENTATION_NOSENSOR,
        };
        for (int orientation : unchanged) {
            assertEquals(orientation, RotationLockHook.respectingRotationLock(orientation));
        }
    }
}
