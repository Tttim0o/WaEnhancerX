package com.waenhancer.xposed.core

import android.content.Context
import android.content.SharedPreferences
import de.robv.android.xposed.XC_MethodHook
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Base abstract class for all modular WAEX enhancements.
 */
abstract class BaseFeature(
    @JvmField protected val context: Context,
    @JvmField protected val classLoader: ClassLoader,
    @JvmField protected val prefs: SharedPreferences
) {
    private val installedHooks = Collections.newSetFromMap(
        ConcurrentHashMap<XC_MethodHook.Unhook, Boolean>()
    )

    /**
     * Initializes and registers the bytecode hooks for this feature.
     */
    @Throws(Throwable::class)
    abstract fun hook()

    abstract val name: String

    open val category: String
        get() = "General"

    protected fun isEnabled(key: String, def: Boolean = false): Boolean {
        return prefs.getBoolean(key, def)
    }

    /** Retains an Xposed hook handle so a lazy feature can be fully removed on screen exit. */
    protected fun <T : XC_MethodHook.Unhook> track(hook: T): T {
        installedHooks.add(hook)
        return hook
    }

    /** Retains all handles returned by hookAllMethods/hookAllConstructors. */
    protected fun <T : Collection<XC_MethodHook.Unhook>> track(hooks: T): T {
        installedHooks.addAll(hooks)
        return hooks
    }

    /** Removes every method hook installed by this feature instance. */
    open fun unhookAll() {
        installedHooks.toList().forEach { hook ->
            try {
                hook.unhook()
            } catch (_: Throwable) {
            }
        }
        installedHooks.clear()
    }
}
