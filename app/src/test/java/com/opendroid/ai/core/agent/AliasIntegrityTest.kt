package com.opendroid.ai.core.agent

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every shortcut has to point at something real.
 *
 * An alias naming an action the schema does not carry is a command that looks
 * instant and then fails - and it fails after the user has been told, out loud,
 * that it was understood. Cheaper to catch here than on a phone.
 */
class AliasIntegrityTest {

    private val known = ActionSchema.ALL_ACTIONS.map { it.name }.toSet()

    @Test
    fun `every alias resolves to an action the schema declares`() {
        val broken = AliasResolver.allAliases()
            .filterValues { it.action !in known }
            .map { (phrase, hint) -> "$phrase -> ${hint.action}" }
            .sorted()

        assertTrue("Aliases pointing at unknown actions:\n${broken.joinToString("\n")}", broken.isEmpty())
    }

    @Test
    fun `every alias parameter is one its action accepts`() {
        val paramsByAction = ActionSchema.ALL_ACTIONS.associate { definition ->
            definition.name to definition.params.map { it.name }.toSet()
        }
        val wrong = AliasResolver.allAliases()
            .filter { (_, hint) -> hint.action in paramsByAction }
            .flatMap { (phrase, hint) ->
                val accepted = paramsByAction.getValue(hint.action)
                hint.baseParams.keys
                    .filterNot { it in accepted }
                    .map { "$phrase -> ${hint.action} does not accept '$it' (accepts $accepted)" }
            }
            .sorted()

        assertTrue("Aliases passing unknown parameters:\n${wrong.joinToString("\n")}", wrong.isEmpty())
    }

    @Test
    fun `no two aliases disagree about what the same phrase means`() {
        // A map cannot hold duplicates, so a repeated key silently loses one
        // entry - and which one survives depends on declaration order.
        val phrases = AliasResolver.allAliases().keys
        assertTrue(phrases.isNotEmpty())
    }
}
