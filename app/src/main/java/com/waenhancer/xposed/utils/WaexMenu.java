package com.waenhancer.xposed.utils;

import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import androidx.annotation.NonNull;

/**
 * The "WAEX" entry in a WhatsApp ⋮ menu. WAEX actions go into its submenu instead of the
 * toolbar, so they never crowd WhatsApp's own icons.
 */
public final class WaexMenu {
    private static final int SUBMENU_ID = 0x57414500;

    private WaexMenu() {}

    /** Returns the WAEX submenu of {@code menu}, adding it first if needed. */
    @NonNull
    public static Menu of(@NonNull Menu menu) {
        try {
            MenuItem existing = menu.findItem(SUBMENU_ID);
            if (existing != null && existing.hasSubMenu()) return existing.getSubMenu();
            SubMenu sub = menu.addSubMenu(Menu.NONE, SUBMENU_ID, Menu.CATEGORY_SECONDARY, "WAEX");
            sub.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            return sub;
        } catch (Throwable t) {
            // A menu without submenu support still gets the items directly.
            return menu;
        }
    }
}
