package com.waenhancer.hooking;

import android.app.Activity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Free-only stand-in: runs registered installs on the first resumed Activity. With the
 * stand-in HookingEngine nothing is registered here, since every feature installs at startup.
 */
public final class LazyHookCoordinator {
    public enum Screen { HOME, CONVERSATION, MEDIA }

    private final List<Runnable> pending = new ArrayList<>();

    public synchronized void register(Screen screen, Runnable install) {
        pending.add(install);
    }

    public void registerDisposer(Screen screen, Consumer<Activity> disposer) {}

    public void onActivityCreated(Activity activity) {}

    public void onActivityResumed(Activity activity) {
        List<Runnable> run;
        synchronized (this) {
            if (pending.isEmpty()) return;
            run = new ArrayList<>(pending);
            pending.clear();
        }
        for (Runnable install : run) install.run();
    }

    public void onActivityDestroyed(Activity activity) {}
}
