package dev.kikugie.ucsm.command

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import dev.kikugie.ucsm.UCSM
import net.minecraft.command.CommandSource
import net.minecraft.text.Text
import java.util.concurrent.CompletableFuture

class CannonConfigArgumentType : ArgumentType<String> {
    val INVALID_CONFIG = SimpleCommandExceptionType(Text.of("Invalid cannon config"))
    val configs: Collection<String>?
        get() = UCSM.cannon?.getExtrasSafe()?.keys

    override fun parse(reader: StringReader): String {
        val remainder = reader.readUnquotedString()
        val keys = configs ?: throw INVALID_CONFIG.create()

        return if (remainder in keys) remainder else
            throw INVALID_CONFIG.create()
    }

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder?
    ): CompletableFuture<Suggestions> =
        CommandSource.suggestMatching(configs ?: throw INVALID_CONFIG.create(), builder)
}