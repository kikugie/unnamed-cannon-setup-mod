package me.kikugie.ucsm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.kikugie.ucsm.mixin.DefaultPosArgumentAccessor;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DefaultPosArgument;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.io.FileNotFoundException;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class Command {
    private static float sqTntRange = 16f;
    private static Vec3i origin = null;
    private static Direction direction = null;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess ignoredAccess) {
        dispatcher.register(literal("ucsm")
                .executes(Command::help)
                .then(literal("reload")
                        .executes(Command::reload))
                .then(literal("explosion_range")
                        .then(argument("range", FloatArgumentType.floatArg(0.1f, 9000f))))
                        .executes(Command::setTntRange)
                .then(literal("origin")
                        .executes(Command::originFromPlayerPos)
                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                .then(argument("direction", new DirectionArgumentType())
                                        .executes(Command::setOrigin))))
                .then(literal("target")
                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                .executes(Command::setTarget))));
    }


    private static int help(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Text.of("§oAre you in need of help?"));
        return 0;
    }

    private static int setTntRange(CommandContext<FabricClientCommandSource> context) {
        float range = context.getArgument("range", Float.class);
        sqTntRange = range * range;
        return 0;
    }

    private static int reload(CommandContext<FabricClientCommandSource> context) {
        origin = null;
        direction = null;
        try {
            CannonMod.initConfig();
            context.getSource().sendFeedback(Text.of("§oConfig reloaded!"));
        } catch (FileNotFoundException e) {
            context.getSource().sendError(Text.of("Error reading config!"));
        }
        return 0;
    }

    private static int setTarget(CommandContext<FabricClientCommandSource> context) {
        if (CannonMod.cannonData == null) {
            context.getSource().sendError(Text.of("Config is not loaded!"));
            return 1;
        }
        if (origin == null || direction == null) {
            context.getSource().sendError(Text.of("Origin is not set up!"));
            return 1;
        }

        Vec3d target = Vec3d.of(getPosFromArgument(context.getArgument("pos", DefaultPosArgument.class), context.getSource()))
                .add(0.5d, 0.5d, 0.5d)
                .subtract(origin.getX(), origin.getY(), origin.getZ())
                .rotateY(-direction.asRotation());

        double squaredDistance = sqTntRange;
        String configuration = null;
        for (Vec3d pos : CannonMod.cannonData.keySet()) {
            var distance = target.squaredDistanceTo(pos);
            if (distance >= squaredDistance) {
                configuration = CannonMod.cannonData.get(pos);
                squaredDistance = distance;
            }
        }
        if (configuration == null) {
            context.getSource().sendError(Text.of("Your target is over 9000 away!")); // ITS OVER 9000 is intentional
            return 1;
        }
        context.getSource().sendFeedback(Text.of("§aConfiguration: " + configuration + ", distance: " + Math.sqrt(squaredDistance)));
        return 0;
    }

    private static int setOrigin(CommandContext<FabricClientCommandSource> context) {
        origin = getPosFromArgument(context.getArgument("pos", DefaultPosArgument.class), context.getSource());
        direction = Direction.byName(context.getArgument("direction", String.class));
        context.getSource().sendFeedback(Text.of("§oOrigin set to " + origin.toShortString() + " facing " + direction.asString()));
        return 0;
    }

    private static int originFromPlayerPos(CommandContext<FabricClientCommandSource> context) {
        final var player = context.getSource().getPlayer();
        origin = player.getBlockPos();
        direction = player.getHorizontalFacing();
        context.getSource().sendFeedback(Text.of("§oOrigin set to " + origin.toShortString() + " facing " + direction.asString()));
        return 0;
    }

    private static BlockPos getPosFromArgument(DefaultPosArgument argument, FabricClientCommandSource source) {

        DefaultPosArgumentAccessor accessor = (DefaultPosArgumentAccessor) argument;
        Vec3d pos = source.getPlayer().getPos();

        return new BlockPos(accessor.getX().toAbsoluteCoordinate(pos.x), accessor.getY().toAbsoluteCoordinate(pos.y), accessor.getZ().toAbsoluteCoordinate(pos.z));
    }
}
