package com.waenhancer.xposed.features.web

import android.content.Context
import com.waenhancer.xposed.core.devkit.DexSearchEngine
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Sends a text message through WhatsApp's own code, the same way its Android Auto reply does:
 * a factory builds the text message and the message sender queues it. Both are found by shape,
 * not by name, because names change with every WhatsApp version. When anything is missing,
 * [available] stays false and the web interface offers to open the chat on the phone instead.
 *
 * Messages are only ever sent for a signed-in person who typed them; nothing is sent on its own.
 */
object WebSender {
    private const val TAG = "[WAEX][Web]"

    @Volatile private var factory: Method? = null
    @Volatile private var sender: Method? = null
    @Volatile private var factoryInstance: Any? = null
    @Volatile private var senderInstance: Any? = null
    @Volatile private var jidParser: Method? = null
    @Volatile private var jidParserTarget: Any? = null
    @Volatile private var jidClass: Class<*>? = null
    @Volatile private var fMessageClass: Class<*>? = null
    @Volatile private var keyConstructor: java.lang.reflect.Constructor<*>? = null
    @Volatile private var lookup: Method? = null
    @Volatile private var lookupInstance: Any? = null
    @Volatile private var bySortId: Method? = null
    @Volatile private var sortInstance: Any? = null
    @Volatile private var deleteForMe: Method? = null
    @Volatile private var deleteForAll: Method? = null
    @Volatile private var deleterInstance: Any? = null
    @Volatile private var reactor: Method? = null
    @Volatile private var reactorInstance: Any? = null

    /** Why the last send or reaction did not work; a short code, never message content. */
    @Volatile var lastError: String = ""
        private set

    val available: Boolean
        get() = factory != null && sender != null && jidClass != null

    val canReact: Boolean
        get() = available && reactor != null && canFind

    val canReply: Boolean
        get() = available && canFind

    val canDelete: Boolean
        get() = deleteForMe != null && canFind

    private val canFind: Boolean
        get() = bySortId != null || (lookup != null && keyConstructor != null)

    /** Finds the pieces. Safe to call once at startup; failures only switch sending off. */
    fun prepare(context: Context, classLoader: ClassLoader) {
        try {
            val engine = DexSearchEngine.getInstance()
            val fMessage = engine.findClassWithCache(context, classLoader, "waex_fmessage_core") { bridge, loader ->
                bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .addUsingString("FMessage/getSenderUserJid/key.id", StringMatchType.Contains)))
                    .firstOrNull()?.getMethodInstance(loader)?.declaringClass
            } ?: return

            // (chat JID, quoted message, ?, text, mentions, timestamp) -> text message
            val foundFactory = engine.findMethodWithCache(context, classLoader, "waex_web_text_factory") { bridge, loader ->
                val matches = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .paramTypes(null, fMessage.name, null, "java.lang.String", "java.util.List", "long")))
                    .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
                    .filter { !Modifier.isStatic(it.modifiers) && fMessage.isAssignableFrom(it.returnType) }
                matches.singleOrNull()
            } ?: return

            // The Android Auto reply calls a small static helper that hands the message to the sender.
            val foundSender = engine.findMethodWithCache(context, classLoader, "waex_web_text_sender") { bridge, loader ->
                val anchor = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .addUsingString("CarTextMessageSenderImpl/sendTextMessage", StringMatchType.Contains)))
                    .firstOrNull() ?: return@findMethodWithCache null
                val candidates = LinkedHashSet<Method>()
                for (helper in anchor.invokes) {
                    val h = runCatching { helper.getMethodInstance(loader) }.getOrNull() ?: continue
                    val hp = h.parameterTypes
                    if (!Modifier.isStatic(h.modifiers) || h.returnType != Void.TYPE || hp.size != 2 || hp[1] != fMessage) continue
                    for (inner in helper.invokes) {
                        val m = runCatching { inner.getMethodInstance(loader) }.getOrNull() ?: continue
                        val p = m.parameterTypes
                        if (!Modifier.isStatic(m.modifiers) && m.returnType == Void.TYPE && p.size == 1 && p[0] == fMessage) {
                            candidates.add(m)
                        }
                    }
                }
                candidates.singleOrNull()
            } ?: return

            val jid = listOf("com.whatsapp.infra.core.jid.Jid", "com.whatsapp.jid.Jid")
                .firstNotNullOfOrNull { runCatching { classLoader.loadClass(it) }.getOrNull() } ?: return

            foundFactory.isAccessible = true
            foundSender.isAccessible = true
            capture(foundFactory.declaringClass) { factoryInstance = it }
            capture(foundSender.declaringClass) { senderInstance = it }
            // Whenever WhatsApp itself sends something, remember the live sender object.
            try {
                XposedBridge.hookMethod(foundSender, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.thisObject?.let { senderInstance = it }
                    }
                })
            } catch (_: Throwable) {
            }
            jidClass = jid
            fMessageClass = fMessage
            factory = foundFactory
            sender = foundSender
            prepareExtras(context, classLoader, engine, fMessage, foundFactory.parameterTypes[0])
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Sending unavailable: ${t.javaClass.simpleName}")
        }
    }

    /** Finds what replies and reactions need: looking a message up by its key, and the reaction sender. */
    private fun prepareExtras(context: Context, classLoader: ClassLoader, engine: DexSearchEngine,
                              fMessage: Class<*>, chatJid: Class<*>) {
        try {
            // The message key is the field of a message built from (chat, id, from me).
            var type: Class<*>? = fMessage
            var key: java.lang.reflect.Constructor<*>? = null
            while (type != null && key == null) {
                for (field in type.declaredFields) {
                    if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) continue
                    key = field.type.declaredConstructors.firstOrNull { c ->
                        val p = c.parameterTypes
                        p.size == 3 && p[0].isAssignableFrom(chatJid) && p[1] == String::class.java &&
                            p[2] == java.lang.Boolean.TYPE
                    }
                    if (key != null) break
                }
                type = type.superclass
            }
            val keyCtor = key ?: return
            keyCtor.isAccessible = true
            val keyClass = keyCtor.declaringClass

            val foundLookup = engine.findMethodWithCache(context, classLoader, "waex_web_message_lookup") { bridge, loader ->
                bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .paramTypes(keyClass.name).returnType(fMessage.name)))
                    .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
                    .filter { m ->
                        val owner = m.declaringClass
                        !Modifier.isStatic(m.modifiers) && !Modifier.isAbstract(owner.modifiers) &&
                            runCatching { owner.getDeclaredConstructor() }.isSuccess &&
                            // The message store implements an interface with this very method.
                            owner.interfaces.any { i ->
                                runCatching { i.getMethod(m.name, keyClass) }.getOrNull()?.returnType == fMessage
                            }
                    }
                    .sortedBy { it.declaringClass.name + "." + it.name }
                    .firstOrNull()
            }
            if (foundLookup != null) {
                foundLookup.isAccessible = true
                capture(foundLookup.declaringClass) { lookupInstance = it }
                keyConstructor = keyCtor
                lookup = foundLookup
            }

            // The message store can also load a message by its sort id, which needs no JID at all.
            // That avoids the phone number / LID mismatch that makes a lookup by key miss.
            val foundBySort = engine.findMethodWithCache(context, classLoader, "waex_web_message_by_sort") { bridge, loader ->
                bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .addUsingString("CachedMessageStore/getMessageBySortId", StringMatchType.Contains)))
                    .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
                    .firstOrNull { m ->
                        !Modifier.isStatic(m.modifiers) && fMessage.isAssignableFrom(m.returnType) &&
                            m.parameterTypes.contentEquals(arrayOf(java.lang.Long.TYPE))
                    }
            }
            if (foundBySort != null) {
                foundBySort.isAccessible = true
                capture(foundBySort.declaringClass) { sortInstance = it }
                bySortId = foundBySort
            }

            // WhatsApp's own "delete for me" and "delete for everyone" (UserActionsMessageDeletion).
            val foundDelete = engine.findMethodWithCache(context, classLoader, "waex_web_delete_for_me") { bridge, loader ->
                bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .addUsingString("UserActionsMessageDeletion/userActionDeleteMessages ", StringMatchType.Equals)))
                    .mapNotNull { runCatching { it.getMethodInstance(loader) }.getOrNull() }
                    .firstOrNull { m ->
                        val p = m.parameterTypes
                        !Modifier.isStatic(m.modifiers) && p.size == 4 && p[0] == Integer::class.java &&
                            Collection::class.java.isAssignableFrom(p[1]) && p[2] == java.lang.Boolean.TYPE && p[3] == java.lang.Boolean.TYPE
                    }
            }
            if (foundDelete != null) {
                foundDelete.isAccessible = true
                val owner = foundDelete.declaringClass
                deleteForAll = owner.declaredMethods.firstOrNull { m ->
                    val p = m.parameterTypes
                    !Modifier.isStatic(m.modifiers) && m.returnType == Void.TYPE && p.size == 3 &&
                        p[0] == Integer::class.java && p[1] == java.util.Set::class.java && p[2] == java.lang.Boolean.TYPE
                }?.also { it.isAccessible = true }
                capture(owner) { deleterInstance = it }
                deleteForMe = foundDelete
            }

            // Same search the double tap reaction feature uses.
            val reactorClass = engine.findClassWithCache(context, classLoader, "waex_action_user_class") { bridge, loader ->
                bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .paramTypes(fMessage.name, "java.lang.String", "boolean")
                    .modifiers(Modifier.PUBLIC or Modifier.FINAL)
                    .returnType("boolean")))
                    .firstOrNull()?.getMethodInstance(loader)?.declaringClass
            }
            val foundReactor = reactorClass?.declaredMethods?.firstOrNull { m ->
                val p = m.parameterTypes
                p.size == 3 && p[0].isAssignableFrom(fMessage) && p[1] == String::class.java &&
                    p[2] == java.lang.Boolean.TYPE
            }
            if (foundReactor != null) {
                foundReactor.isAccessible = true
                capture(foundReactor.declaringClass) { reactorInstance = it }
                reactor = foundReactor
            }
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Replies and reactions unavailable: ${t.javaClass.simpleName}")
        }
    }

    /** Remembers the instance WhatsApp creates itself, which is preferred over making our own. */
    private fun capture(type: Class<*>, store: (Any) -> Unit) {
        try {
            XposedBridge.hookAllConstructors(type, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.thisObject?.let(store)
                }
            })
        } catch (_: Throwable) {
        }
    }

    private fun instanceOf(type: Class<*>, captured: Any?): Any {
        if (captured != null) return captured
        val constructor = type.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
    }

    /**
     * Turns "123@s.whatsapp.net" into WhatsApp's JID object of the type [wanted]. The parser is a
     * method taking one string; it sits on the JID classes or on their companion objects.
     */
    private fun parseJid(raw: String, wanted: Class<*>): Any? {
        val jid = jidClass ?: return null
        jidParser?.let { known ->
            val result = runCatching { known.invoke(jidParserTarget, raw) }.getOrNull()
            if (wanted.isInstance(result)) return result
        }
        // Classes to look at: the wanted type up to Jid, each with its static companion fields.
        val targets = ArrayList<Pair<Class<*>, Any?>>()
        var type: Class<*>? = wanted
        while (type != null && jid.isAssignableFrom(type)) {
            targets.add(type to null)
            for (field in type.declaredFields) {
                if (!Modifier.isStatic(field.modifiers) || field.type.isPrimitive ||
                    field.type == String::class.java) continue
                val value = runCatching { field.isAccessible = true; field.get(null) }.getOrNull() ?: continue
                targets.add(value.javaClass to value)
            }
            type = type.superclass
        }
        for ((holder, instance) in targets) {
            for (method in holder.declaredMethods) {
                val p = method.parameterTypes
                if (p.size != 1 || p[0] != String::class.java || !jid.isAssignableFrom(method.returnType)) continue
                val static = Modifier.isStatic(method.modifiers)
                if (!static && instance == null) continue
                try {
                    method.isAccessible = true
                    val target = if (static) null else instance
                    val result = method.invoke(target, raw)
                    if (wanted.isInstance(result)) {
                        jidParserTarget = target
                        jidParser = method
                        return result
                    }
                } catch (_: Throwable) {
                }
            }
        }
        return null
    }

    /** A message WhatsApp already has: by sort id when possible, otherwise by its key. */
    private fun findMessage(target: Key): Any? {
        bySortId?.let { find ->
            if (target.sortId > 0) {
                val found = runCatching { find.invoke(instanceOf(find.declaringClass, sortInstance), target.sortId) }.getOrNull()
                if (found != null) return found
            }
        }
        val ctor = keyConstructor ?: return null
        val find = lookup ?: return null
        val jid = parseJid(target.chatJid, ctor.parameterTypes[0]) ?: return null
        val key = ctor.newInstance(jid, target.keyId, target.fromMe)
        return find.invoke(instanceOf(find.declaringClass, lookupInstance), key)
    }

    private fun fail(code: String, t: Throwable? = null): Boolean {
        val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
        lastError = if (cause == null) code else code + ":" + cause.javaClass.simpleName
        XposedBridge.log("$TAG $lastError")
        return false
    }

    /** One message as the database names it: chat JID, key id, direction and sort id. */
    class Key(val chatJid: String, val keyId: String, val fromMe: Boolean, val sortId: Long = 0)

    /**
     * Queues [text] for [rawJid], as a reply to [quoted] when given. Returns false when
     * WhatsApp's send path could not be used; [lastError] then says which step failed.
     */
    fun sendText(rawJid: String, text: String, quoted: Key? = null): Boolean {
        val make = factory ?: return fail("send_no_factory")
        val send = sender ?: return fail("send_no_sender")
        return try {
            val jid = parseJid(rawJid, make.parameterTypes[0]) ?: return fail("send_jid_not_parsed")
            val quotedMessage = quoted?.let {
                findMessage(it) ?: return fail("send_quoted_not_found")
            }
            val message = make.invoke(instanceOf(make.declaringClass, factoryInstance),
                jid, quotedMessage, null, text, null, System.currentTimeMillis())
                ?: return fail("send_no_message")
            val live = senderInstance != null
            send.invoke(instanceOf(send.declaringClass, senderInstance), message)
            XposedBridge.log("$TAG send handed over (${if (live) "live" else "new"} sender, ${message.javaClass.name})")
            lastError = ""
            true
        } catch (t: Throwable) {
            fail("send_failed", t)
        }
    }

    /**
     * Deletes a message the way WhatsApp's own dialog does: for this phone only, or with
     * [everyone] for all (only the user's own messages, as WhatsApp allows).
     */
    fun delete(target: Key, everyone: Boolean): Boolean {
        val forMe = deleteForMe ?: return fail("delete_unavailable")
        if (everyone && (deleteForAll == null || !target.fromMe)) return fail("delete_everyone_unavailable")
        return try {
            val message = findMessage(target) ?: return fail("delete_message_not_found")
            val owner = instanceOf(forMe.declaringClass, deleterInstance)
            if (everyone) deleteForAll!!.invoke(owner, null, java.util.Collections.singleton(message), false)
            else forMe.invoke(owner, null, java.util.Collections.singletonList(message), false, false)
            lastError = ""
            true
        } catch (t: Throwable) {
            fail("delete_failed", t)
        }
    }

    /** Adds [emoji] to a message as the user's reaction; an empty string removes it. */
    fun react(target: Key, emoji: String): Boolean {
        val method = reactor ?: return fail("react_unavailable")
        return try {
            val message = findMessage(target)
                ?: return fail("react_message_not_found")
            // WhatsApp shows the change itself, so this runs where it expects: on the main thread.
            val done = java.util.concurrent.CountDownLatch(1)
            var error: Throwable? = null
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    method.invoke(instanceOf(method.declaringClass, reactorInstance), message, emoji, emoji.isNotEmpty())
                } catch (t: Throwable) {
                    error = t
                } finally {
                    done.countDown()
                }
            }
            if (!done.await(5, java.util.concurrent.TimeUnit.SECONDS)) return fail("react_timeout")
            error?.let { return fail("react_failed", it) }
            lastError = ""
            true
        } catch (t: Throwable) {
            fail("react_failed", t)
        }
    }
}
