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
    @Volatile private var jidClass: Class<*>? = null

    val available: Boolean
        get() = factory != null && sender != null && jidClass != null

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
            jidClass = jid
            factory = foundFactory
            sender = foundSender
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Sending unavailable: ${t.javaClass.simpleName}")
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

    /** Turns "123@s.whatsapp.net" into WhatsApp's JID object of the type the factory expects. */
    private fun parseJid(raw: String, wanted: Class<*>): Any? {
        val jid = jidClass ?: return null
        jidParser?.let { known ->
            val result = runCatching { known.invoke(null, raw) }.getOrNull()
            if (wanted.isInstance(result)) return result
        }
        val holders = LinkedHashSet<Class<*>>()
        holders.add(jid)
        for (field in jid.declaredFields) {
            if (Modifier.isStatic(field.modifiers) && !field.type.isPrimitive) holders.add(field.type)
        }
        for (holder in holders) {
            for (method in holder.declaredMethods) {
                val p = method.parameterTypes
                if (!Modifier.isStatic(method.modifiers) || p.size != 1 || p[0] != String::class.java) continue
                if (!jid.isAssignableFrom(method.returnType)) continue
                try {
                    method.isAccessible = true
                    val result = method.invoke(null, raw)
                    if (wanted.isInstance(result)) {
                        jidParser = method
                        return result
                    }
                } catch (_: Throwable) {
                }
            }
        }
        return null
    }

    /** Queues [text] for [rawJid]. Returns false when WhatsApp's send path could not be used. */
    fun sendText(rawJid: String, text: String): Boolean {
        val make = factory ?: return false
        val send = sender ?: return false
        return try {
            val jid = parseJid(rawJid, make.parameterTypes[0]) ?: return false
            val message = make.invoke(instanceOf(make.declaringClass, factoryInstance),
                jid, null, null, text, null, System.currentTimeMillis()) ?: return false
            send.invoke(instanceOf(send.declaringClass, senderInstance), message)
            true
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            XposedBridge.log("$TAG Send failed: ${cause.javaClass.simpleName}")
            false
        }
    }
}
