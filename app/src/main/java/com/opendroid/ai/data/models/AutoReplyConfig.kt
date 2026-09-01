package com.opendroid.ai.data.models

data class AutoReplyConfig(
    // Auto-reply is opt-in: replying to untrusted incoming messages with LLM
    // output is a prompt-injection / data-exfiltration surface, so every
    // channel stays off until the user explicitly enables it.
    val globalEnabled: Boolean = false,
    val whatsappEnabled: Boolean = false,
    val smsEnabled: Boolean = false,
    val emailEnabled: Boolean = false,
    val replyDelayMinutes: Int = 15,
    val blacklistedContacts: Set<String> = emptySet(),
    /**
     * The only contacts that may ever be auto-replied to.
     *
     * An empty list means nobody, not everybody. Replying on someone's behalf
     * is not a thing to switch on for every stranger who messages them, and a
     * setting whose empty state is "all contacts" is one bad tap away from
     * exactly that.
     */
    val whitelistedContacts: Set<String> = emptySet(),
    /**
     * What the owner wants said about themselves - who they are, what they do,
     * who these people are to them. Given to the model as background, never
     * quoted into a reply.
     */
    val personaNotes: String? = null,
    /**
     * How the owner writes: short or long, emoji or none, formal or not, the
     * words they actually use. The most useful thing here is two or three of
     * their own messages pasted verbatim - a model imitates an example far
     * better than it follows an adjective.
     */
    val styleNotes: String? = null,
    /** Per-contact background, keyed by the contact name as WhatsApp shows it. */
    val contactNotes: Map<String, String> = emptyMap(),
    val customPrompt: String? = null,
    val maxRepliesPerContactPerHour: Int = 3
)
