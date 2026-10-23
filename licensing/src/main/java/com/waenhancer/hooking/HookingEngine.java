package com.waenhancer.hooking;

/**
 * Free-only stand-in: the real engine (private) defers hooks to the screens that need them.
 * Here every feature installs at startup, the behavior before hooking modes existed.
 */
public final class HookingEngine {
    public enum Installation { STARTUP, LAZY_HOME, LAZY_CONVERSATION, LAZY_MEDIA }

    /** Work a feature needs before WhatsApp's UI starts; none in the stand-in. */
    public interface PreHook {
        void apply(android.content.SharedPreferences prefs);
    }

    public static final class FeaturePolicy {
        public final String className;
        public final PreHook preHook = null;

        FeaturePolicy(String className) {
            this.className = className;
        }
    }

    private HookingEngine() {}

    public static FeaturePolicy policyFor(String className) {
        return new FeaturePolicy(className);
    }

    public static Installation effectiveInstallation(FeaturePolicy policy, String mode) {
        return Installation.STARTUP;
    }

    public static LazyHookCoordinator.Screen screenFor(Installation installation) {
        switch (installation) {
            case LAZY_CONVERSATION: return LazyHookCoordinator.Screen.CONVERSATION;
            case LAZY_MEDIA: return LazyHookCoordinator.Screen.MEDIA;
            default: return LazyHookCoordinator.Screen.HOME;
        }
    }
}
