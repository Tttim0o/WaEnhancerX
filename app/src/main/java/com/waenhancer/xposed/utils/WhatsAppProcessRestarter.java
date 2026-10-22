package com.waenhancer.xposed.utils;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import de.robv.android.xposed.XposedBridge;

/** Terminates the current WhatsApp process, then relaunches it in a clean process. */
public final class WhatsAppProcessRestarter {
    private WhatsAppProcessRestarter() {}

    public static void restart(@NonNull Activity activity) {
        try {
            Intent launchIntent = activity.getPackageManager()
                    .getLaunchIntentForPackage(activity.getPackageName());
            Intent restartIntent = launchIntent != null && launchIntent.getComponent() != null
                    ? Intent.makeRestartActivityTask(launchIntent.getComponent())
                    : new Intent(activity, activity.getClass())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            restartIntent.setPackage(activity.getPackageName());

            PendingIntent pendingRestart = PendingIntent.getActivity(
                    activity.getApplicationContext(),
                    0x57414558,
                    restartIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarms = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            if (alarms == null) throw new IllegalStateException("AlarmManager unavailable");
            alarms.set(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 500L, pendingRestart);
            activity.finishAffinity();
        } catch (Throwable throwable) {
            XposedBridge.log("[WAEX] WhatsApp clean restart scheduling failed: "
                    + throwable.getMessage());
            return;
        }

        Process.killProcess(Process.myPid());
        System.exit(0);
    }
}
