package com.opendroid.ai.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import javax.inject.Inject

/**
 * Pure node-tree traversal/matching logic used by [OpenDroidAccessibilityService],
 * extracted so it can be exercised in tests without a bound accessibility service.
 *
 * Every function here takes the root [AccessibilityNodeInfo] as a parameter instead
 * of reading `rootInActiveWindow` itself, so a test can hand it either the real
 * service's root or a root obtained straight from `UiAutomation` - same behavior,
 * no service required. See #66 (prototype) / #105 (this extraction).
 *
 * Stateless and dependency-free: production wires it in through Hilt, tests can
 * just `AccessibilityNodeTraversal()` it directly.
 *
 * Callers remain responsible for recycling the root node they obtained; this class
 * only recycles the intermediate/child nodes it creates while walking.
 */
class AccessibilityNodeTraversal @Inject constructor() {

    /** Concatenates the text/contentDescription of every node in the tree, depth-first. */
    fun screenText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val sb = StringBuilder()
        collectText(root, sb)
        return sb.toString()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val nodeText = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()

        if (!nodeText.isNullOrEmpty()) {
            sb.append(nodeText).append("\n")
        } else if (!contentDesc.isNullOrEmpty()) {
            sb.append(contentDesc).append("\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, sb)
            child.recycle()
        }
    }

    /**
     * Finds the first node matching [text] (or the nearest clickable ancestor,
     * for leaves that aren't themselves clickable) and performs ACTION_CLICK on it.
     * Returns whether a click was dispatched.
     */
    /**
     * Clicks the best match for [text], not the first one.
     *
     * `findAccessibilityNodeInfosByText` matches contentDescription as well as
     * text, and returns nodes in tree order. In a WhatsApp chat list the avatar
     * is described "Foto profil Istri" and is itself clickable, so it came
     * first and opening a conversation opened the profile picture instead.
     *
     * So every match is scored and the best one wins: a node whose own text is
     * the label beats one that merely describes a picture of it.
     */
    fun findAndClick(root: AccessibilityNodeInfo?, text: String): Boolean {
        if (root == null) return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isEmpty()) return false

        val best = nodes.maxByOrNull { scoreMatch(it, text) }
        var clicked = false
        if (best != null && scoreMatch(best, text) > REJECT_BELOW) {
            clicked = clickSelfOrAncestor(best)
        }
        nodes.forEach { runCatching { it.recycle() } }
        return clicked
    }

    private fun clickSelfOrAncestor(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                val done = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                runCatching { parent?.recycle() }
                return done
            }
            val next = parent.parent
            runCatching { parent?.recycle() }
            parent = next
        }
        return false
    }

    /**
     * How well a node answers "the thing labelled [text]".
     *
     * Own text outranks a description, an exact label outranks a fragment, and
     * an image is pushed below both: a picture described with someone's name is
     * a picture of them, not the row that opens their conversation.
     */
    private fun scoreMatch(node: AccessibilityNodeInfo, text: String): Int {
        val wanted = text.trim().lowercase()
        val own = node.text?.toString()?.trim()?.lowercase()
        val described = node.contentDescription?.toString()?.trim()?.lowercase()
        val className = node.className?.toString().orEmpty()

        var score = when {
            own == wanted -> 100
            own?.startsWith(wanted) == true -> 80
            own?.contains(wanted) == true -> 60
            described == wanted -> 40
            described?.contains(wanted) == true -> 20
            else -> 0
        }
        if (IMAGE_CLASSES.any { className.contains(it) }) score -= 50
        if (described != null && PICTURE_WORDS.any { described.contains(it) }) score -= 40
        if (!node.isVisibleToUser) score -= 30
        return score
    }

    /** Below this nothing on screen actually carries the label. */
    private val REJECT_BELOW = 0

    private val IMAGE_CLASSES = listOf("ImageView", "ImageButton")

    /** In both languages the app is used in. */
    private val PICTURE_WORDS = listOf(
        "photo", "picture", "avatar", "image",
        "foto", "gambar", "profil",
    )

    /**
     * Walks the tree depth-first for a clickable node whose text or
     * contentDescription (case-insensitive) is one of [labels], and clicks it.
     * This is the IME-submit fallback used when ACTION_IME_ENTER isn't
     * available or fails.
     */
    fun findAndClickSubmitControl(root: AccessibilityNodeInfo?, labels: List<String>): Boolean {
        if (root == null) return false
        return findSubmitNode(root, labels)
    }

    private fun findSubmitNode(node: AccessibilityNodeInfo, labels: List<String>): Boolean {
        val text = node.text?.toString()?.lowercase()
        val contentDesc = node.contentDescription?.toString()?.lowercase()
        if (node.isClickable && (labels.contains(text) || labels.contains(contentDesc))) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findSubmitNode(child, labels)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }
}
