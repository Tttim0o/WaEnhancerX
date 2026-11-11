package com.waenhancer.xposed.features.homescreen

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.waenhancer.app.R
import com.waenhancer.utils.ContactNameResolver
import com.waenhancer.utils.UiText
import com.waenhancer.xposed.core.BaseFeature
import com.waenhancer.xposed.core.components.WaexBottomSheet
import com.waenhancer.xposed.core.devkit.DexSearchEngine
import com.waenhancer.xposed.features.web.WebAccounts
import com.waenhancer.xposed.features.web.WebData
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONObject
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.ClassMatcher
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * "All accounts" inbox inside WhatsApp, for people with more than one account in WhatsApp
 * (WhatsApp's own "add account"; not WhatsApp Business).
 *
 * WhatsApp's own chat list stays as it is. The three-dot menu of the home screen gets an
 * "All accounts" entry that opens one list with the chats of every account, newest first:
 *  - a chat of the account in use opens directly;
 *  - a chat of another account switches WhatsApp to that account (WhatsApp's own switch screen,
 *    which restarts WhatsApp) and then opens the chat;
 *  - holding a one-to-one chat offers "Write from…" to message that person from another account.
 *
 * Other accounts are read from their saved databases, read-only. They show what WhatsApp had when
 * that account was last in use. Nothing read here is logged.
 */
class MergedInboxHook(context: Context, classLoader: ClassLoader, prefs: SharedPreferences) :
    BaseFeature(context, classLoader, prefs) {

    override val name: String get() = "Merged Inbox"
    override val category: String get() = "Customization"

    private val accounts by lazy { WebAccounts(context.applicationContext ?: context) }
    private val readers = HashMap<String, WebData>()

    override fun hook() {
        if (context.packageName != WHATSAPP) return
        if (!isEnabled(PREF_KEY, false) && !isEnabled(WEB_MERGE_KEY, false)) return
        val home = resolveHomeActivity() ?: run {
            XposedBridge.log("$TAG home screen not found")
            return
        }
        val menuHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                if (!home.isInstance(activity)) return
                val menu = param.args.getOrNull(0) as? Menu ?: return
                menu.removeItem(MENU_ID)
                if (accounts.list().count { !it.loggedOut } < 2) return
                menu.add(Menu.NONE, MENU_ID, Menu.NONE, UiText.get(R.string.waex_ui_merged_inbox, "All accounts"))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
        }
        hookUp(home, "onCreateOptionsMenu", arrayOf(Menu::class.java), menuHook)
        hookUp(home, "onPrepareOptionsMenu", arrayOf(Menu::class.java), menuHook)
        hookUp(home, "onOptionsItemSelected", arrayOf(MenuItem::class.java), object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                if (!home.isInstance(activity)) return
                val item = param.args.getOrNull(0) as? MenuItem ?: return
                if (item.itemId != MENU_ID) return
                param.result = true
                show(activity)
            }
        })
        // After a switch made from the list, open the chat that was tapped.
        hookUp(home, "onPostResume", arrayOf(), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                if (home.isInstance(activity)) openPending(activity)
            }
        })
    }

    // ─── Data ───────────────────────────────────────────────────────────

    private class Row(
        val account: WebAccounts.Account,
        val jid: String,
        val target: String,
        val name: String,
        val group: Boolean,
        val time: Long,
        val unread: Int,
        val last: String
    )

    private fun reader(account: WebAccounts.Account): WebData = synchronized(readers) {
        readers.getOrPut(account.id + if (account.active) ":active" else "") {
            if (account.active) WebData(context) else WebData(context, account)
        }
    }

    /** Every chat of every account, newest first. Archived and locked chats are left out. */
    private fun load(): List<Row> {
        val rows = ArrayList<Row>()
        for (account in accounts.list()) {
            if (account.loggedOut) continue
            try {
                val data = reader(account)
                for (chat in data.chats()) {
                    if (chat.archived) continue
                    rows.add(Row(
                        account = account,
                        jid = chat.jid,
                        target = data.sendTarget(chat),
                        name = data.nameOf(chat.jid, chat.subject),
                        group = ContactNameResolver.isGroupJid(chat.jid),
                        time = chat.sort,
                        unread = chat.unread,
                        last = chat.lastText?.replace('\n', ' ')?.take(120).orEmpty()
                    ))
                }
            } catch (_: Throwable) {
            }
        }
        rows.sortByDescending { it.time }
        return rows
    }

    // ─── Opening chats and switching ────────────────────────────────────

    private fun open(activity: Activity, row: Row) {
        if (row.account.active) {
            openChat(activity, row.target, null)
            return
        }
        confirmSwitch(activity, row.account) { remember(row.account, row.target, null) }
    }

    /** "Write from…": the same person from another account (one-to-one chats only). */
    private fun writeFrom(activity: Activity, row: Row) {
        val phone = phoneOf(row.jid) ?: return
        val choices = accounts.list().filter { !it.loggedOut }
        val labels: Array<CharSequence> = choices.map<WebAccounts.Account, CharSequence> { a ->
            a.name + if (a.active) "" else "  ·  " + UiText.get(R.string.waex_ui_merged_not_in_use, "not in use")
        }.toTypedArray()
        WaexBottomSheet(activity).asBottomSheet()
            .setTitle(UiText.get(R.string.waex_ui_merged_write_from, "Write from…"))
            .setItems(labels) { dialog, which ->
                dialog.dismiss()
                val account = choices[which]
                if (account.active) openChat(activity, null, phone)
                else confirmSwitch(activity, account) { remember(account, null, phone) }
            }
            .show()
    }

    private fun confirmSwitch(activity: Activity, account: WebAccounts.Account, before: () -> Unit) {
        WaexBottomSheet(activity).asBottomSheet()
            .setTitle(UiText.get(R.string.waex_ui_merged_switch_title, "Switch to %s?", account.name))
            .setMessage(UiText.get(R.string.waex_ui_merged_switch_text,
                "WhatsApp switches to this account and restarts. The chat opens when it is back."))
            .setPositiveButton(UiText.get(R.string.waex_ui_merged_switch, "Switch")) { dialog, _ ->
                dialog.dismiss()
                before()
                try {
                    val intent = Intent().setClassName(activity.packageName,
                        "com.whatsapp.accountswitching.routing.AccountSwitchingRoutingActivity")
                        .putExtra("switch_to_account_dir_id", account.dirId)
                    account.lid?.let { intent.putExtra("switch_to_account_lid", it) }
                    activity.startActivity(intent)
                } catch (_: Throwable) {
                    pendingFile().delete()
                    Toast.makeText(activity, UiText.get(R.string.waex_ui_merged_switch_failed,
                        "WhatsApp could not switch accounts."), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(UiText.get(R.string.action_cancel, "Cancel")) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    /** Opens a chat by its JID, or a one-to-one chat by phone number through WhatsApp's own link. */
    private fun openChat(activity: Activity, jid: String?, phone: String?) {
        try {
            if (jid != null) {
                val conversation = XposedHelpers.findClassIfExists("com.whatsapp.Conversation", classLoader)
                if (conversation != null) {
                    activity.startActivity(Intent(activity, conversation).putExtra("jid", jid)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    return
                }
            }
            val number = phone ?: jid?.let(::phoneOf) ?: return
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$number"))
                .setPackage(activity.packageName))
        } catch (_: Throwable) {
        }
    }

    private fun phoneOf(jid: String): String? =
        jid.takeIf { it.endsWith("@s.whatsapp.net") }?.substringBefore('@')?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }

    /** Kept in WhatsApp's main data folder, which stays the same for every account. */
    private fun pendingFile() = File(context.applicationInfo.dataDir, "waex_merged_open.json")

    private fun remember(account: WebAccounts.Account, jid: String?, phone: String?) {
        try {
            pendingFile().writeText(JSONObject()
                .put("account", account.id).put("jid", jid ?: "").put("phone", phone ?: "")
                .put("at", System.currentTimeMillis()).toString())
        } catch (_: Throwable) {
        }
    }

    private fun openPending(activity: Activity) {
        val file = pendingFile()
        if (!file.isFile) return
        val json = try { JSONObject(file.readText()) } catch (_: Throwable) { file.delete(); return }
        if (System.currentTimeMillis() - json.optLong("at") > 3 * 60_000) { file.delete(); return }
        if (accounts.list().firstOrNull { it.active }?.id != json.optString("account")) return
        file.delete()
        val jid = json.optString("jid").takeIf { it.isNotEmpty() }
        val phone = json.optString("phone").takeIf { it.isNotEmpty() }
        activity.window?.decorView?.postDelayed({ openChat(activity, jid, phone) }, 600)
    }

    // ─── The list ───────────────────────────────────────────────────────

    private fun show(activity: Activity) {
        val dark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val bg = if (dark) Color.parseColor("#0B141A") else Color.WHITE
        val fg = if (dark) Color.parseColor("#E9EDEF") else Color.parseColor("#111B21")
        val muted = if (dark) Color.parseColor("#8696A0") else Color.parseColor("#667781")
        val accent = Color.parseColor("#00A884")
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics).toInt()

        val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(16), dp(8))
        }
        val close = ImageButton(activity).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(fg)
            contentDescription = UiText.get(R.string.action_cancel, "Cancel")
            setOnClickListener { dialog.dismiss() }
        }
        val title = TextView(activity).apply {
            text = UiText.get(R.string.waex_ui_merged_inbox, "All accounts")
            setTextColor(fg)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), 0, 0, 0)
        }
        bar.addView(close)
        bar.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bar)
        val hint = TextView(activity).apply {
            text = UiText.get(R.string.waex_ui_merged_hint,
                "Accounts not in use show what they had when last in use. Hold a chat to write from another account.")
            setTextColor(muted)
            textSize = 12f
            setPadding(dp(16), 0, dp(16), dp(8))
        }
        root.addView(hint)
        val holder = FrameLayout(activity)
        val progress = ProgressBar(activity)
        holder.addView(progress, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        root.addView(holder, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        dialog.setContentView(root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()

        Thread({
            val rows = try { load() } catch (_: Throwable) { emptyList() }
            activity.runOnUiThread {
                if (!dialog.isShowing) return@runOnUiThread
                holder.removeAllViews()
                if (rows.isEmpty()) {
                    holder.addView(TextView(activity).apply {
                        text = UiText.get(R.string.waex_ui_merged_empty, "No chats found.")
                        setTextColor(muted)
                        gravity = Gravity.CENTER
                    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    return@runOnUiThread
                }
                val list = ListView(activity).apply {
                    divider = null
                    adapter = RowAdapter(activity, rows, fg, muted, accent, dark, ::dp)
                    setOnItemClickListener { _, _, position, _ ->
                        dialog.dismiss()
                        open(activity, rows[position])
                    }
                    setOnItemLongClickListener { _, _, position, _ ->
                        val row = rows[position]
                        if (row.group || phoneOf(row.jid) == null) return@setOnItemLongClickListener false
                        dialog.dismiss()
                        writeFrom(activity, row)
                        true
                    }
                }
                holder.addView(list)
            }
        }, "waex-merged-inbox").apply { isDaemon = true }.start()
    }

    private class RowAdapter(
        private val activity: Activity,
        private val rows: List<Row>,
        private val fg: Int,
        private val muted: Int,
        private val accent: Int,
        private val dark: Boolean,
        private val dp: (Int) -> Int
    ) : BaseAdapter() {
        private val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
        private val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)

        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = rows[position]
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))
            }
            var hash = 0
            for (ch in row.jid) hash = hash * 31 + ch.code
            val circle = TextView(activity).apply {
                text = (row.name.trim().firstOrNull() ?: '?').uppercase()
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.HSVToColor(floatArrayOf((Math.abs(hash) % 360).toFloat(), 0.45f, 0.62f)))
                }
            }
            line.addView(circle, LinearLayout.LayoutParams(dp(46), dp(46)))
            val texts = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
            }
            val top = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(TextView(activity).apply {
                text = row.name
                setTextColor(fg)
                textSize = 16f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val today = DateFormat.getDateInstance().format(Date())
            top.addView(TextView(activity).apply {
                text = if (DateFormat.getDateInstance().format(Date(row.time)) == today) timeFormat.format(Date(row.time))
                    else dateFormat.format(Date(row.time))
                setTextColor(if (row.unread > 0) accent else muted)
                textSize = 12f
            })
            texts.addView(top)
            val bottom = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            bottom.addView(TextView(activity).apply {
                text = row.account.name
                setTextColor(if (row.account.active) accent else muted)
                textSize = 11f
                setPadding(dp(6), dp(1), dp(6), dp(1))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(if (row.account.active) Color.argb(46, 0, 168, 132)
                        else if (dark) Color.argb(40, 255, 255, 255) else Color.argb(20, 0, 0, 0))
                }
            })
            bottom.addView(TextView(activity).apply {
                text = row.last
                setTextColor(muted)
                textSize = 14f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(6), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (row.unread > 0) bottom.addView(TextView(activity).apply {
                text = row.unread.toString()
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(6), 0, dp(6), 0)
                minWidth = dp(20)
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(accent) }
            })
            texts.addView(bottom)
            line.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return line
        }
    }

    // ─── Hooks ──────────────────────────────────────────────────────────

    private fun resolveHomeActivity(): Class<*>? {
        try {
            val found = DexSearchEngine.getInstance().findClassWithCache(context, classLoader, "waex_home_activity_class") { bridge, loader ->
                val data = bridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                    .className(".HomeActivity", StringMatchType.EndsWith))).firstOrNull()
                    ?: bridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                        .className("HomeActivity", StringMatchType.Contains))).firstOrNull()
                data?.getInstance(loader)
            }
            if (found != null) return found
        } catch (_: Throwable) {
        }
        return XposedHelpers.findClassIfExists("com.whatsapp.home.ui.HomeActivity", classLoader)
            ?: XposedHelpers.findClassIfExists("com.whatsapp.HomeActivity", classLoader)
    }

    /** Hooks [name] where it is declared: on the class or the nearest parent that has it. */
    private fun hookUp(leaf: Class<*>, name: String, params: Array<Class<*>>, callback: XC_MethodHook) {
        var current: Class<*>? = leaf
        while (current != null && current != Any::class.java) {
            try {
                XposedBridge.hookMethod(current.getDeclaredMethod(name, *params), callback)
                return
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            } catch (_: Throwable) {
                return
            }
        }
    }

    companion object {
        private const val TAG = "[WAEX][MergedInbox]"
        const val PREF_KEY = "merged_inbox"
        /** The same switch in the WAEX Web settings turns this on too. */
        private const val WEB_MERGE_KEY = "web_merge_accounts"
        private const val WHATSAPP = "com.whatsapp"
        private const val MENU_ID = 0x7EAE0010
    }
}
