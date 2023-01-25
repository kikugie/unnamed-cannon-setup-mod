package me.kikugie.ucsm;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class DirectionArgumentType implements ArgumentType<String> {
    public static final List<String> dirStrings = Stream.of(
            Direction.NORTH,
            Direction.WEST,
            Direction.SOUTH,
            Direction.EAST
    ).map(Direction::asString).toList();

    private final DynamicCommandExceptionType UNEXPECTED_VALUE_EXCEPTION = new DynamicCommandExceptionType(
            component -> Text.translatable("Invalid direction: %s", component)
    );

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String remainder = reader.getRemaining().trim().toLowerCase();
        if (dirStrings.contains(remainder)) {
            return reader.readString();
        }
        throw UNEXPECTED_VALUE_EXCEPTION.create(remainder);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(dirStrings, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return dirStrings;
    }
}
