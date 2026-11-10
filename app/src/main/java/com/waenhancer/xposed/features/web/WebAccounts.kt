package com.waenhancer.xposed.features.web

import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.json.JSONObject
import java.io.File

/**
 * The WhatsApp accounts on this phone (WhatsApp's own "add account" feature), read from the files
 * WhatsApp keeps for its account switcher:
 *  - `app_account_switching/accounts`: JSON, `allAccounts` holds one JSON string per account with
 *    `dir_id`, `lid`, `jid`, `name`, `is_logged_out`.
 *  - `app_account_switching/active_account`: the `dir_id` of the account in use.
 *  - Each account's data lives in `accounts/<dir_id>/`; the first account (empty `dir_id`) in the
 *    app's own data folder.
 *
 * Only one account runs at a time. The others are read from their saved databases, so they show
 * what WhatsApp had when that account was last in use. Switching goes through WhatsApp's own
 * routing screen, the same one its notifications for other accounts open.
 */
class WebAccounts(private val app: Context) {

    class Account(
        /** Stable id for the browser: the dir id, or "main" for the first account. */
        val id: String,
        val dirId: String,
        val name: String,
        val phone: String?,
        val lid: String?,
        val loggedOut: Boolean,
        val active: Boolean,
        val dataDir: File
    ) {
        val dbFile: File get() = File(dataDir, "databases/msgstore.db")
        val contactsFile: File get() = File(dataDir, "databases/wa.db")
    }

    private val root: File = File(app.applicationInfo.dataDir)
    private val switchDir: File get() = File(root, "app_account_switching")

    @Volatile private var cached: List<Account> = emptyList()
    @Volatile private var cachedAt = 0L

    /** All accounts, the active one first. A phone with one account gives a list of one. */
    fun list(): List<Account> {
        val now = System.currentTimeMillis()
        if (now - cachedAt < 5_000 && cached.isNotEmpty()) return cached
        val result = try { read() } catch (_: Throwable) { emptyList() }
        cached = result
        cachedAt = now
        return result
    }

    fun active(): Account? = list().firstOrNull { it.active }

    fun byId(id: String?): Account? = if (id.isNullOrEmpty()) active() else list().firstOrNull { it.id == id }

    private fun read(): List<Account> {
        val file = File(switchDir, "accounts")
        if (!file.isFile) return emptyList()
        val activeDir = File(switchDir, "active_account").takeIf { it.isFile }?.readText()?.trim().orEmpty()
        val json = JSONObject(file.readText())
        val all = json.optJSONArray("allAccounts") ?: return emptyList()
        val list = ArrayList<Account>()
        for (i in 0 until all.length()) {
            val entry = try { JSONObject(all.getString(i)) } catch (_: Throwable) { continue }
            val dirId = entry.optString("dir_id").trim()
            if (dirId.contains('/') || dirId.contains("..")) continue
            val jid = entry.optString("jid").takeIf { it.isNotBlank() }
            val name = entry.optString("name").takeIf { it.isNotBlank() }
                ?: jid?.substringBefore('@')?.let { "+$it" } ?: "WhatsApp"
            val dir = if (dirId.isEmpty()) root else File(root, "accounts/$dirId")
            list.add(Account(
                id = dirId.ifEmpty { MAIN },
                dirId = dirId,
                name = name,
                phone = jid?.substringBefore('@')?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }?.let { "+$it" },
                lid = entry.optString("lid").takeIf { it.isNotBlank() },
                loggedOut = entry.optBoolean("is_logged_out"),
                active = dirId == activeDir,
                dataDir = dir
            ))
        }
        // Without an active_account file the first account is the one in use.
        if (list.none { it.active } && list.isNotEmpty()) {
            val main = list.firstOrNull { it.dirId.isEmpty() } ?: list.first()
            list[list.indexOf(main)] = Account(main.id, main.dirId, main.name, main.phone, main.lid,
                main.loggedOut, true, main.dataDir)
        }
        return list.sortedByDescending { it.active }
    }

    /**
     * Asks WhatsApp to switch to [account]. WhatsApp checks it, restarts itself and comes back with
     * that account; this server starts again with it. False when Android refused to open the screen.
     */
    fun switchTo(account: Account): Boolean {
        if (account.active || account.loggedOut) return false
        return try {
            val intent = Intent().setClassName(app.packageName,
                "com.whatsapp.accountswitching.routing.AccountSwitchingRoutingActivity")
                .putExtra("switch_to_account_dir_id", account.dirId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            account.lid?.let { intent.putExtra("switch_to_account_lid", it) }
            cachedAt = 0
            // Android does not let WhatsApp open a screen from the background; the WAEX app opens it.
            val pending = PendingIntent.getActivity(app, account.id.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val extras = Bundle()
            extras.putParcelable("intent", pending)
            extras.putString("label", account.name)
            val answer = try {
                app.contentResolver.call(Uri.parse("content://com.waenhancer.hookprovider"), "web_launch", null, extras)
            } catch (_: Throwable) {
                null
            }
            if (answer?.getBoolean("success", false) == true) return true
            app.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        const val MAIN = "main"
    }
}
