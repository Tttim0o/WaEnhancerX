package com.waenhancer.config;

/** Free-only stand-in: hooking modes; the Pro modes fall back without a license. */
public final class HookingModePolicy {
    public static final String KEY = "hooking_mode";
    public static final String CUSTOMIZATIONS_KEY = "hooking_mode_customizations";
    public static final String LIGHTWEIGHT = "lightweight";
    public static final String BALANCED = "balanced";
    public static final String COMPATIBILITY = "compatibility";
    public static final String CUSTOMIZED = "customized";

    private HookingModePolicy() {}

    public static String normalize(String mode, boolean isPro) {
        if (LIGHTWEIGHT.equals(mode) || CUSTOMIZED.equals(mode)) return isPro ? mode : COMPATIBILITY;
        if (COMPATIBILITY.equals(mode)) return COMPATIBILITY;
        return BALANCED;
    }
}
