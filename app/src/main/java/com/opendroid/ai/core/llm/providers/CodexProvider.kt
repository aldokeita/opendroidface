// Codex, reached through the bridge running on the owner's computer.
//
// This is worth being exact about, because the setting says "Codex" and the
// obvious reading is that the phone talks to Codex directly:
//
//   It does not. A ChatGPT Plus subscription is not an API credential, and there
//   is no flow by which an Android app signs into a ChatGPT account and spends
//   that plan. What signs in is the Codex CLI, on a computer, through its own
//   browser login. The bridge in tools/codex-bridge puts an OpenAI-shaped
//   endpoint in front of that CLI, and this provider talks to the bridge.
//
// So the transport is exactly the Custom OpenAI one - same protocol, same
// requests - and all this class changes is which stored endpoint and key it
// reads, so Codex and a genuine custom endpoint can be configured at the same
// time without overwriting each other.

package com.opendroid.ai.core.llm.providers

import com.opendroid.ai.data.repository.SettingsRepository
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CodexProvider @Inject constructor(
    client: OkHttpClient,
    settingsRepository: SettingsRepository,
) : CustomOpenAIProvider(client, settingsRepository) {

    override val name: String get() = PROVIDER_NAME

    companion object {
        const val PROVIDER_NAME = "Codex"
    }
}
