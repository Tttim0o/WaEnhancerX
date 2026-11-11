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
        val activeDir = File(switchDir, "active_account").takeIf { it.isFile }?.readText()?.trim().orEmpty()
            .takeIf { !it.contains('/') && !it.contains("..") }.orEmpty()
        val list = ArrayList<Account>()
        val file = File(switchDir, "accounts")
        val all = try {
            if (file.isFile) JSONObject(file.readText()).optJSONArray("allAccounts") else null
        } catch (_: Throwable) {
            null
        }
        if (all != null) for (i in 0 until all.length()) {
            val entry = try { all.optJSONObject(i) ?: JSONObject(all.getString(i)) } catch (_: Throwable) { continue }
            val dirId = entry.optString("dir_id").trim()
            if (dirId.contains('/') || dirId.contains("..")) continue
            if (list.any { it.dirId == dirId }) continue
            // WhatsApp often leaves these empty for the accounts added later; their own settings have them.
            val own = ownPrefs(dirFor(dirId))
            val jid = entry.optString("jid").takeIf { it.isNotBlank() }
                ?: own["registration_jid"]
                ?: own["ph"]?.let { (own["cc"] ?: "") + it }
            val name = entry.optString("name").takeIf { it.isNotBlank() }
                ?: own["push_name"]
                ?: jid?.substringBefore('@')?.let { "+$it" } ?: "WhatsApp"
            list.add(Account(
                id = dirId.ifEmpty { MAIN },
                dirId = dirId,
                name = name,
                phone = jid?.substringBefore('@')?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }?.let { "+$it" },
                lid = entry.optString("lid").takeIf { it.isNotBlank() } ?: own["self_lid"],
                loggedOut = entry.optBoolean("is_logged_out"),
                active = dirId == activeDir,
                dataDir = dirFor(dirId)
            ))
        }
        // Accounts WhatsApp keeps a folder for but that the list above did not name.
        File(root, "accounts").listFiles()?.forEach { dir ->
            val dirId = dir.name
            if (!dir.isDirectory || list.any { it.dirId == dirId }) return@forEach
            if (!File(dir, "databases/msgstore.db").isFile) return@forEach
            val own = ownPrefs(dir)
            val phone = (own["registration_jid"] ?: own["ph"]?.let { (own["cc"] ?: "") + it })
                ?.substringBefore('@')?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }?.let { "+$it" }
            list.add(Account(dirId, dirId, own["push_name"] ?: phone ?: ("WhatsApp " + (list.size + 1)), phone,
                own["self_lid"], false, dirId == activeDir, dir))
        }
        // WhatsApp's list holds the other accounts; the one in use may be missing from it.
        if (list.none { it.active }) {
            list.add(Account(
                id = activeDir.ifEmpty { MAIN },
                dirId = activeDir,
                name = ownName() ?: CURRENT,
                phone = null,
                lid = null,
                loggedOut = false,
                active = true,
                dataDir = dirFor(activeDir)
            ))
        }
        if (list.size != lastLogged) {
            lastLogged = list.size
            // Counts only, never names or numbers.
            de.robv.android.xposed.XposedBridge.log("[WAEX][Web] accounts: ${list.size} (listed ${all?.length() ?: -1})")
        }
        return list.sortedByDescending { it.active }
    }

    @Volatile private var lastLogged = -1

    private fun dirFor(dirId: String): File = if (dirId.isEmpty()) root else File(root, "accounts/$dirId")

    /**
     * A few text values from an account's own settings file, read as a file because the settings of
     * an account that is not in use are not loaded. Missing file or keys give an empty map.
     */
    private fun ownPrefs(dataDir: File): Map<String, String> = try {
        val file = File(dataDir, "shared_prefs/" + app.packageName + "_preferences_light.xml")
        if (!file.isFile || file.length() > 4_000_000) emptyMap() else {
            val wanted = setOf("push_name", "registration_jid", "self_lid", "cc", "ph")
            val found = HashMap<String, String>()
            Regex("<string name=\"([a-z_]+)\">([^<]*)</string>").findAll(file.readText()).forEach { m ->
                val key = m.groupValues[1]
                val value = m.groupValues[2].replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                    .replace("&apos;", "'").replace("&amp;", "&").trim()
                if (key in wanted && value.isNotEmpty()) found[key] = value
            }
            found
        }
    } catch (_: Throwable) {
        emptyMap()
    }

    /** The profile name WhatsApp shows for the account in use, when it can be found. */
    private fun ownName(): String? = try {
        listOf(app.packageName + "_preferences_light", app.packageName + "_preferences")
            .firstNotNullOfOrNull { name ->
                app.getSharedPreferences(name, Context.MODE_PRIVATE).getString("push_name", null)?.takeIf { it.isNotBlank() }
            }
    } catch (_: Throwable) {
        null
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
        private const val CURRENT = "WhatsApp"
    }
}
