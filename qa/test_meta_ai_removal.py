#!/usr/bin/env python3
"""Protect the layered Meta AI removal contract."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOOK = ROOT / "app/src/main/java/com/waenhancer/xposed/features/homescreen/ChatListCustomizationsHook.java"
REGISTRY = ROOT / "app/src/main/java/com/waenhancer/xposed/core/FeatureRegistry.java"
STRINGS = ROOT / "app/src/main/res/values/meta_ai_control.xml"

hook = HOOK.read_text(encoding="utf-8")
registry = REGISTRY.read_text(encoding="utf-8")
strings = STRINGS.read_text(encoding="utf-8")

contracts = {
    "host preference disabled": 'HOST_PREFERENCE.equals(param.args[0])' in hook
    and "param.setResult(false)" in hook,
    "new views removed": '"onAttachedToWindow"' in hook and "removeView(view)" in hook,
    "existing hierarchy rescanned": '"onPostResume"' in hook and "removeAssistantViews(decor)" in hook,
    "layout space collapsed": "params.width = 0" in hook and "params.height = 0" in hook,
    "assistant navigation blocked": "isAssistantConversation" in hook and "activity.finish()" in hook,
    "known FAB resource handled": 'case "fab_second"' in hook,
    "boolean feature gates disabled": all(
        f"setBooleanOverride({property_id}, metaAiDisabled, false)" in registry
        for property_id in (8025, 6251, 8026, 14886)
    ),
    "integer feature gate disabled":
        "setIntegerOverride(15535, metaAiDisabled, 0)" in registry,
    "preference is named Remove Meta AI":
        '<string name="feat_metaai_title">Remove Meta AI</string>' in strings,
    "summary matches the complete behavior": all(
        phrase in strings for phrase in ("Disable Meta AI", "buttons", "entry points", "direct conversation access")
    ),
}

missing = [name for name, present in contracts.items() if not present]
if missing:
    raise SystemExit("Meta AI removal contract violations:\n- " + "\n- ".join(missing))

print(f"PASS: {len(contracts)} layered Meta AI removal contracts are present")
