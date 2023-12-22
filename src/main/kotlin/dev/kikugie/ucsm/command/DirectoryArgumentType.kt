package dev.kikugie.ucsm.command

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandSource
import net.minecraft.text.Text
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class DirectoryArgumentType(val root: Path) : ArgumentType<Path> {
    private val INVALID_DIRECTORY =
        DynamicCommandExceptionType { component: Any ->
            Text.of(
                "Directory doesn't exist: ${
                    MinecraftClient.getInstance().runDirectory.toPath().relativize(root.resolve(component.toString()))
                }"
            )
        }
    val dirs: List<String>
        get() = root.listDirectoryEntries().filter { it.isDirectory() }.map { it.name }

    override fun parse(reader: StringReader): Path {
        val remainder = reader.readUnquotedString()
        return if (remainder in dirs) root.resolve(remainder)
        else throw INVALID_DIRECTORY.create(remainder)
    }

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder?
    ): CompletableFuture<Suggestions> = CommandSource.suggestMatching(dirs, builder)
}