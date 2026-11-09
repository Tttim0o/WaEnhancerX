package com.waenhancer.xposed.features.conversation;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListPopupWindow;
import androidx.annotation.NonNull;
import com.waenhancer.config.QuickReplies;
import com.waenhancer.xposed.core.BaseFeature;
import com.waenhancer.xposed.features.media.VoiceDictationHook;
import com.waenhancer.xposed.utils.ActivityTracker;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Typing "/" in a chat's message field lists the saved replies from WAEX; tapping one replaces
 * the "/word" with its text. Only Android views are used, so no obfuscated names are needed.
 */
public class QuickRepliesHook extends BaseFeature {
    private static final String TAG = "[WAEX][QuickReplies]";
    private static final String FIELD_ATTACHED = "waex_quick_replies";

    /** Set when this feature hit an unexpected error; it then stays off until restart. */
    private final AtomicBoolean disabled = new AtomicBoolean(false);
    private WeakReference<ListPopupWindow> popupRef = new WeakReference<>(null);
    private String cachedRaw;
    private List<QuickReplies.Item> cachedItems = new ArrayList<>();

    public QuickRepliesHook(@NonNull Context context, @NonNull ClassLoader classLoader,
                            @NonNull SharedPreferences prefs) {
        super(context, classLoader, prefs);
    }

    @NonNull @Override public String getName() { return "Quick Replies"; }

    @Override
    public void hook() {
        ActivityTracker.addListener(activity -> guard(() -> {
            if (!activity.getClass().getSimpleName().contains("Conversation")) return;
            if (!isEnabled(QuickReplies.ENABLED_KEY, true)) return;
            attach(activity, 0);
        }));
    }

    private void attach(Activity activity, int attempt) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        EditText field = VoiceDictationHook.findMessageField(activity);
        if (field == null) {
            // The footer can be inflated after onResume.
            if (attempt < 3) {
                activity.getWindow().getDecorView().postDelayed(
                        () -> guard(() -> attach(activity, attempt + 1)), 400L * (attempt + 1));
            }
            return;
        }
        if (XposedHelpers.getAdditionalInstanceField(field, FIELD_ATTACHED) != null) return;
        XposedHelpers.setAdditionalInstanceField(field, FIELD_ATTACHED, Boolean.TRUE);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                guard(() -> onTextChanged(field, s));
            }
        });
    }

    private void onTextChanged(EditText field, Editable text) {
        if (disabled.get() || !isEnabled(QuickReplies.ENABLED_KEY, true)) {
            dismiss();
            return;
        }
        QuickReplies.Token token = QuickReplies.tokenAt(text, field.getSelectionEnd());
        List<QuickReplies.Item> matches = token == null
                ? new ArrayList<>() : QuickReplies.matches(items(), token.query);
        if (matches.isEmpty()) {
            dismiss();
        } else {
            show(field, matches);
        }
    }

    private List<QuickReplies.Item> items() {
        String raw = prefs.getString(QuickReplies.PREF_KEY, "");
        if (raw == null) raw = "";
        if (!raw.equals(cachedRaw)) {
            cachedItems = QuickReplies.decode(raw);
            cachedRaw = raw;
        }
        return cachedItems;
    }

    private void show(EditText field, List<QuickReplies.Item> matches) {
        List<String> rows = new ArrayList<>();
        for (QuickReplies.Item item : matches) {
            String preview = item.text.replace('\n', ' ');
            if (preview.length() > 60) preview = preview.substring(0, 60) + "…";
            rows.add("/" + item.shortcut + "  ·  " + preview);
        }
        ListPopupWindow popup = popupRef.get();
        if (popup == null || popup.getAnchorView() != field) {
            dismiss();
            popup = new ListPopupWindow(field.getContext());
            popup.setAnchorView(field);
            popup.setModal(false);
            // Keeps the keyboard open and the popup above it.
            popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NEEDED);
            popupRef = new WeakReference<>(popup);
        }
        final ListPopupWindow shown = popup;
        shown.setAdapter(new ArrayAdapter<>(field.getContext(),
                android.R.layout.simple_list_item_1, rows));
        shown.setWidth(Math.max(field.getWidth(), 1));
        shown.setOnItemClickListener((parent, view, position, id) -> guard(() -> {
            shown.dismiss();
            Editable current = field.getText();
            QuickReplies.Token token = QuickReplies.tokenAt(current, field.getSelectionEnd());
            if (token == null || position >= matches.size()) return;
            current.replace(token.start, token.end, matches.get(position).text);
        }));
        shown.show();
    }

    private void dismiss() {
        ListPopupWindow popup = popupRef.get();
        if (popup != null && popup.isShowing()) popup.dismiss();
    }

    /** Runs WhatsApp-side work; an unexpected error turns the feature off until restart. */
    private void guard(Runnable work) {
        if (disabled.get()) return;
        try {
            work.run();
        } catch (Throwable t) {
            disabled.set(true);
            XposedBridge.log(TAG + " disabled until restart: " + t);
            try {
                dismiss();
            } catch (Throwable ignored) {}
        }
    }
}
