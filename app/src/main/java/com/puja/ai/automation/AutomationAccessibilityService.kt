package com.puja.ai.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Sirf tab chalti hai jab user ise Android Settings me khud enable kare.
 * Events ko ignore karti hai (kuch padhti/store/bhejti nahi). Sirf user ke command par:
 * Home/Back/Recents/Notifications, text se click, scroll. Password fields skip hote hain.
 */
class AutomationAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Jaan-bujhkar kuch nahi: hum screen events monitor nahi karte.
    }

    override fun onInterrupt() {}

    fun perform(kind: String, target: String?): Boolean = when (kind) {
        "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "CLICK" -> target?.let { clickByText(it) } ?: false
        "SCROLL_DOWN" -> scroll(forward = true)
        "SCROLL_UP" -> scroll(forward = false)
        else -> false
    }

    private fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (node in root.findAccessibilityNodeInfosByText(text)) {
            if (node.isPassword) continue
            var cur: AccessibilityNodeInfo? = node
            while (cur != null) {
                if (cur.isClickable && !cur.isPassword && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
                cur = cur.parent
            }
        }
        return false
    }

    private fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findScrollable(root) ?: return false
        return node.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }

    private fun findScrollable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findScrollable(c)?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile
        var instance: AutomationAccessibilityService? = null
    }
}
