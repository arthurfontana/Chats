package com.arthurfontana.claudescheduler.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.arthurfontana.claudescheduler.data.PendingMessageStore
import com.arthurfontana.claudescheduler.util.Constants

/**
 * Automação por acessibilidade: localiza o campo de texto e o botão de enviar
 * na tela do app do Claude e simula a digitação + toque. É inerentemente frágil —
 * se o layout do app do Claude mudar, os seletores abaixo podem parar de encontrar
 * os elementos e o envio simplesmente não acontece (sem crash, best-effort).
 */
class ClaudeAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var attemptRunnable: Runnable? = null
    private var attemptsLeft = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        if (packageName !in Constants.CLAUDE_PACKAGE_NAMES) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val store = PendingMessageStore(this)
        if (store.peekPending() == null) return

        scheduleAttempt(store, delayMs = Constants.INJECT_DELAY_MS, resetAttempts = true)
    }

    private fun scheduleAttempt(store: PendingMessageStore, delayMs: Long, resetAttempts: Boolean) {
        if (resetAttempts) {
            attemptRunnable?.let { handler.removeCallbacks(it) }
            attemptsLeft = MAX_ATTEMPTS
        }
        val runnable = Runnable { attemptInjection(store) }
        attemptRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun attemptInjection(store: PendingMessageStore) {
        val message = store.peekPending() ?: return
        attemptsLeft--

        val root = rootInActiveWindow
        val editNode = root?.let { findEditableNode(it) }

        if (editNode == null) {
            if (attemptsLeft > 0) scheduleAttempt(store, delayMs = RETRY_DELAY_MS, resetAttempts = false)
            return
        }

        setText(editNode, message)

        handler.postDelayed({
            val freshRoot = rootInActiveWindow
            val sendButton = freshRoot?.let { findSendButton(it) }
            if (sendButton != null) {
                performClick(sendButton)
                store.clear()
            } else if (attemptsLeft > 0) {
                scheduleAttempt(store, delayMs = RETRY_DELAY_MS, resetAttempts = false)
            }
        }, SEND_BUTTON_DELAY_MS)
    }

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun performClick(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findNode(root) { node -> node.isEditable && node.className?.contains("EditText") == true }

    private fun findSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        findNode(root) { node ->
            if (!node.isClickable) return@findNode false
            val desc = node.contentDescription?.toString()?.lowercase()
            val text = node.text?.toString()?.lowercase()
            SEND_KEYWORDS.any { keyword -> desc?.contains(keyword) == true || text?.contains(keyword) == true }
        }

    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    override fun onInterrupt() {}

    companion object {
        private val SEND_KEYWORDS = listOf("send", "enviar")
        private const val MAX_ATTEMPTS = 12
        private const val RETRY_DELAY_MS = 500L
        private const val SEND_BUTTON_DELAY_MS = 350L
    }
}
