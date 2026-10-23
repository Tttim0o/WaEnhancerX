package com.waenhancer.xposed.features.media;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CallScreenNameTest {

    @Test
    public void callScreensAreRecognised() {
        assertTrue(CallRecordButton.isCallScreenName("VoipActivityV2"));
        assertTrue(CallRecordButton.isCallScreenName("CallActivity"));
        assertTrue(CallRecordButton.isCallScreenName("GroupCallScreenActivity"));
        assertTrue(CallRecordButton.isCallScreenName("InCallActivity"));
    }

    @Test
    public void callListsAndOtherScreensAreNot() {
        assertFalse(CallRecordButton.isCallScreenName("CallLogActivity"));
        assertFalse(CallRecordButton.isCallScreenName("CallsListActivity"));
        assertFalse(CallRecordButton.isCallScreenName("Conversation"));
        assertFalse(CallRecordButton.isCallScreenName("HomeActivity"));
    }
}
