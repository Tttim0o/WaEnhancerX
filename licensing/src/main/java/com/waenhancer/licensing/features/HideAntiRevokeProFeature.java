package com.waenhancer.licensing.features;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.WrapperListAdapter;

/** Free-only stand-in: the Pro "hide anti-revoke" mode is never active. */
public final class HideAntiRevokeProFeature {

    public interface HostBridge {
        String resolveConversationJid(Activity activity);
        Activity getCurrentConversation();
        boolean isAntiRevokeAvailable();
        void refreshConversation(Activity activity);
    }

    public interface MessageBridge {
        String getMessageKeyId(Object message);
        long getRevokedTimestamp(String keyId);
        boolean isAntiRevokeAvailable();
    }

    private HideAntiRevokeProFeature() {}

    public static void install(ClassLoader classLoader, SharedPreferences prefs, HostBridge bridge) {}

    public static void refreshConversationRows(Activity activity, Object adapter, Object listView) {}

    public static void attachAdapterItemHooks(ListAdapter boundAdapter, ListAdapter underlyingAdapter,
                                              ClassLoader classLoader, SharedPreferences prefs,
                                              MessageBridge bridge) {}

    public static boolean isHiddenForActiveConversation(SharedPreferences prefs) { return false; }

    public static void observeNativeDeletedRow(ViewGroup row, Object message) {}

    public static ViewGroup renderDeletedMessageRow(ViewGroup row, Object message, String keyId,
                                                    long deletedTs, ClassLoader classLoader) {
        return row;
    }

    /** Free anti-revoke needs the real adapter behind header/footer wrappers. */
    public static ListAdapter getUnderlyingAdapter(ListAdapter adapter) {
        ListAdapter current = adapter;
        while (current instanceof WrapperListAdapter) {
            ListAdapter inner = ((WrapperListAdapter) current).getWrappedAdapter();
            if (inner == null || inner == current) break;
            current = inner;
        }
        return current;
    }
}
