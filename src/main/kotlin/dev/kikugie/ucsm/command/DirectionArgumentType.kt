package dev.kikugie.ucsm.command

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.CommandSource
import net.minecraft.text.Text
import net.minecraft.util.math.Direction
import java.util.concurrent.CompletableFuture

class DirectionArgumentType : ArgumentType<Direction> {
    private val UNEXPECTED_VALUE_EXCEPTION =
        DynamicCommandExceptionType { component: Any -> Text.of("Invalid direction: $component") }

    @Throws(CommandSyntaxException::class)
    override fun parse(reader: StringReader): Direction {
        val remainder = reader.readUnquotedString()
        return if (remainder in dirStrings) Direction.byName(remainder)!! else
            throw UNEXPECTED_VALUE_EXCEPTION.create(remainder)
    }

    override fun <S> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        return CommandSource.suggestMatching(dirStrings, builder)
    }

    override fun getExamples() = dirStrings

    companion object {
        val dirStrings = Direction.entries.filter { it.axis.isHorizontal }.map { it.getName() }
    }
}
