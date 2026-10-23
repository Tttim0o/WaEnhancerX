package com.waenhancer.xposed.core.devkit;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import de.robv.android.xposed.XposedBridge;
import org.luckypray.dexkit.DexKitBridge;
import java.io.File;
import java.lang.reflect.Method;

/**
 * Singleton wrapper around DexKitBridge for runtime signature-based class and method unobfuscation.
 */
public final class DexSearchEngine {

    private static volatile DexSearchEngine sInstance;
    private volatile DexKitBridge mBridge;
    /** True once a hook or lookup asked for the bridge (prewarming alone does not count). */
    private volatile boolean mRequested;

    /** Stored in the DexCacheManager file, so a host update clears it with the cache. */
    private static final String PREFS_NAME = "waex_dex_cache";
    private static final String KEY_USED_AT_STARTUP = "dexkit_used_at_startup";

    static {
        try {
            System.loadLibrary("dexkit");
        } catch (Throwable ignored) {}
    }

    private DexSearchEngine() {}

    public static DexSearchEngine getInstance() {
        if (sInstance == null) {
            synchronized (DexSearchEngine.class) {
                if (sInstance == null) {
                    sInstance = new DexSearchEngine();
                }
            }
        }
        return sInstance;
    }

    public static void loadLibrary(@NonNull Context context) {
        try {
            String nativeLibraryDir = context.getPackageManager()
                    .getApplicationInfo("com.waenhancer", 0).nativeLibraryDir;
            File libFile = new File(nativeLibraryDir, "libdexkit.so");
            if (libFile.exists()) {
                System.load(libFile.getAbsolutePath());
            }
        } catch (Throwable t) {
            try {
                System.loadLibrary("dexkit");
            } catch (Throwable ignored) {}
        }
    }

    public void initialize(@NonNull Context context) {
        mRequested = true;
        create(context);
    }

    private synchronized void create(@NonNull Context context) {
        if (mBridge == null) {
            try {
                loadLibrary(context);
                String apkPath = context.getApplicationInfo().sourceDir;
                mBridge = DexKitBridge.create(apkPath);
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] Failed to initialize DexKitBridge: " + t.getMessage());
            }
        }
    }

    /**
     * Opening the bridge parses the whole host APK. When the previous start of this host
     * version needed it, open it on a background thread as soon as a Context exists, so the
     * work overlaps the host's own startup instead of blocking hook installation later.
     * Lookups still run exactly as before; {@link #initialize} waits for this to finish.
     */
    public static void prewarmIfUsedLastStart(@NonNull Context context) {
        Thread thread = new Thread(() -> {
            try {
                if (prefs(context).getLong(KEY_USED_AT_STARTUP, 0L) == 1L) {
                    getInstance().create(context);
                }
            } catch (Throwable t) {
                XposedBridge.log("[WAEX] DexKit prewarm skipped: " + t.getClass().getSimpleName());
            }
        }, "WAEX-DexKit-Prewarm");
        thread.setDaemon(true);
        thread.start();
    }

    /** Call once feature hooks are installed: remembers whether this start needed DexKit. */
    public void recordStartupUsage(@NonNull Context context) {
        try {
            long used = mRequested ? 1L : 0L;
            SharedPreferences prefs = prefs(context);
            if (prefs.getLong(KEY_USED_AT_STARTUP, -1L) != used) {
                prefs.edit().putLong(KEY_USED_AT_STARTUP, used).apply();
            }
        } catch (Throwable ignored) {}
    }

    private static SharedPreferences prefs(Context context) {
        Context app = context.getApplicationContext();
        return (app != null ? app : context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Nullable
    public DexKitBridge getBridge() {
        return mBridge;
    }

    /**
     * Resolves a method using DexCacheManager Tier 1 cache, falling back to DexKit scanning Tier 2.
     */
    @Nullable
    public Method findMethodWithCache(
            @NonNull Context context,
            @NonNull ClassLoader loader,
            @NonNull String cacheKey,
            @NonNull DexKitQuery query
    ) {
        DexCacheManager cache = DexCacheManager.getInstance(context);
        return cache.getMethod(loader, cacheKey, () -> {
            if (mBridge == null) {
                initialize(context);
            }
            if (mBridge == null) return null;
            return query.execute(mBridge, loader);
        });
    }

    /**
     * Resolves a Class using DexCacheManager Tier 1 cache, falling back to DexKit scanning Tier 2.
     */
    @Nullable
    public Class<?> findClassWithCache(
            @NonNull Context context,
            @NonNull ClassLoader loader,
            @NonNull String cacheKey,
            @NonNull DexKitClassQuery query
    ) {
        DexCacheManager cache = DexCacheManager.getInstance(context);
        return cache.getClass(loader, cacheKey, () -> {
            if (mBridge == null) {
                initialize(context);
            }
            if (mBridge == null) return null;
            return query.execute(mBridge, loader);
        });
    }

    /** Resolves and persists multiple methods produced by one DexKit query. */
    @Nullable
    public Method[] findMethodsWithCache(
            @NonNull Context context,
            @NonNull ClassLoader loader,
            @NonNull String cacheKey,
            @NonNull DexKitMethodsQuery query
    ) {
        DexCacheManager cache = DexCacheManager.getInstance(context);
        return cache.getMethods(loader, cacheKey, () -> {
            if (mBridge == null) initialize(context);
            if (mBridge == null) return null;
            return query.execute(mBridge, loader);
        });
    }

    /** Resolves and persists multiple classes produced by one DexKit query. */
    @Nullable
    public Class<?>[] findClassesWithCache(
            @NonNull Context context,
            @NonNull ClassLoader loader,
            @NonNull String cacheKey,
            @NonNull DexKitClassesQuery query
    ) {
        DexCacheManager cache = DexCacheManager.getInstance(context);
        return cache.getClasses(loader, cacheKey, () -> {
            if (mBridge == null) initialize(context);
            if (mBridge == null) return null;
            return query.execute(mBridge, loader);
        });
    }

    public synchronized void close() {
        if (mBridge != null) {
            try {
                mBridge.close();
            } catch (Throwable ignored) {}
            mBridge = null;
        }
    }

    @FunctionalInterface
    public interface DexKitQuery {
        Method execute(@NonNull DexKitBridge bridge, @NonNull ClassLoader loader) throws Throwable;
    }

    @FunctionalInterface
    public interface DexKitClassQuery {
        Class<?> execute(@NonNull DexKitBridge bridge, @NonNull ClassLoader loader) throws Throwable;
    }

    @FunctionalInterface
    public interface DexKitMethodsQuery {
        Method[] execute(@NonNull DexKitBridge bridge, @NonNull ClassLoader loader) throws Throwable;
    }

    @FunctionalInterface
    public interface DexKitClassesQuery {
        Class<?>[] execute(@NonNull DexKitBridge bridge, @NonNull ClassLoader loader) throws Throwable;
    }
}
