// Who may be replied to on the owner's behalf, and when the assistant should
// say nothing at all.
//
// These are the two decisions in auto-reply that must not depend on a model's
// judgement, so they live here as plain functions with tests. A wrong answer
// sends a message, in someone's name, to a person who did not ask to be talked
// to by software.

package com.opendroid.ai.core.agent

import com.opendroid.ai.data.models.AutoReplyConfig

object AutoReplyPolicy {

    /**
     * The token a model returns instead of a reply when it cannot tell what is
     * being asked. Deliberately not a phrase - anything conversational could be
     * something the model genuinely meant to send.
     */
    const val SKIP_TOKEN = "SKIP"

    /**
     * Whether [contact] is on the allowlist.
     *
     * An empty allowlist means nobody. That is the whole point of the feature
     * as asked for - certain contacts, not all of them - and it is also the
     * safer reading of an empty box: a list nobody has filled in should not
     * authorise more than one that has.
     *
     * Matching ignores case and surrounding whitespace, and ignores emoji and
     * punctuation, because a WhatsApp contact called "Sayangku 🫶🏻" is the same
     * person as "Sayangku" and nobody should have to reproduce an emoji exactly
     * to make their own allowlist work.
     */
    fun isAllowed(contact: String?, config: AutoReplyConfig): Boolean {
        if (config.whitelistedContacts.isEmpty()) return false
        val wanted = normalizeContact(contact ?: return false)
        if (wanted.isEmpty()) return false
        if (config.blacklistedContacts.any { normalizeContact(it) == wanted }) return false
        return config.whitelistedContacts.any { normalizeContact(it) == wanted }
    }

    /** The owner's note about this contact, matched the same forgiving way. */
    fun noteFor(contact: String?, config: AutoReplyConfig): String? {
        val wanted = normalizeContact(contact ?: return null)
        return config.contactNotes.entries
            .firstOrNull { normalizeContact(it.key) == wanted }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Whether what the model produced should actually be sent.
     *
     * A model asked to stay quiet tends to explain that it is staying quiet, so
     * a reply that is only the skip token - with or without punctuation around
     * it - is treated as silence rather than sent as a one-word message.
     */
    fun shouldSend(reply: String?): Boolean {
        val trimmed = reply?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        val bare = trimmed.trim('"', '\'', '.', '!', ' ')
        return !bare.equals(SKIP_TOKEN, ignoreCase = true)
    }

    /**
     * Contact names as they compare: lowercase letters and digits only.
     *
     * WhatsApp names carry emoji, skin-tone modifiers, zero-width joiners and
     * decorative punctuation, none of which identifies anyone.
     */
    fun normalizeContact(raw: String): String = raw
        .lowercase()
        .filter { it.isLetterOrDigit() || it.isWhitespace() }
        .replace(Regex("\\s+"), " ")
        .trim()
}
