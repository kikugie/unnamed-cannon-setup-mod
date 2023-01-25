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
import net.minecraft.world.RaycastContext;

import java.io.FileNotFoundException;

import static me.kikugie.ucsm.CannonMod.configs;
import static me.kikugie.ucsm.CannonMod.points;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class Command {
    private static float sqTntRange = 400f;
    private static Vec3i origin = null;
    private static Direction direction = null;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess ignoredAccess) {
        dispatcher.register(literal("ucsm")
                .executes(Command::help)
                .then(literal("reload")
                        .executes(Command::reload))
                .then(literal("precision")
                        .then(argument("range", FloatArgumentType.floatArg(0.1f, 9000f))))
                .executes(Command::setTntRange)
                .then(literal("origin")
                        .executes(Command::originFromPlayerPos)
                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                .then(argument("direction", new DirectionArgumentType())
                                        .executes(Command::setOrigin))))
                .then(literal("target")
                        .executes(Command::raycastTarget)
                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                .executes(Command::posTarget))));
    }


    private static int help(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Text.of("§oAre you in need of help?"));
        return 0;
    }

    private static int setTntRange(CommandContext<FabricClientCommandSource> context) {
        float range = context.getArgument("range", Float.class);
        sqTntRange = range * range;
        return 1;
    }

    private static int reload(CommandContext<FabricClientCommandSource> context) {
        origin = null;
        direction = null;
        if (CannonMod.initConfig()) {
            context.getSource().sendFeedback(Text.of("§oConfig reloaded!"));
            return 1;
        }
        context.getSource().sendError(Text.of("Error reading config!"));
        return 1;
    }

    private static int raycastTarget(CommandContext<FabricClientCommandSource> context) {
        var src = context.getSource();
        var player = src.getPlayer();
        BlockPos result = player.world.raycast(
                new RaycastContext(
                        player.getEyePos(),
                        player.getEyePos().
                                add(player.getRotationVecClient().
                                        multiply(1000f)),
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.ANY,
                        player)
        ).getBlockPos();

        if (result.getSquaredDistance(player.getEyePos()) >= 990 * 990) {
            context.getSource().sendError(Text.of("You are looking into nowhere..."));
            return -1;
        }

        return getTarget(result, src) ? 1 : -1;
    }

    private static int posTarget(CommandContext<FabricClientCommandSource> context) {
        var src = context.getSource();
        return getTarget(getPosFromArgument(context.getArgument("pos", DefaultPosArgument.class), src), src) ? 1 : -1;
    }

    private static boolean getTarget(BlockPos pos, FabricClientCommandSource source) {
        if (points == null || configs == null) {
            source.sendError(Text.of("Config is not loaded!"));
            return false;
        }
        if (origin == null || direction == null) {
            source.sendError(Text.of("Origin is not set up!"));
            return false;
        }

        // God forgive me
        int rotate = switch (direction) {
            case SOUTH -> 180;
            case EAST -> 90;
            case WEST -> -90;
            default -> 0;
        };

        Vec3d target = Vec3d.of(pos)
                .subtract(origin.getX() - 0.5, origin.getY() - 0.5, origin.getZ() - 0.5)
                .rotateY(rotate);
        double requiredDistance = sqTntRange;
        String configuration = null;

        for (int i = 0; i < CannonMod.points.length; i++) {
            var distance = target.squaredDistanceTo(points[i]);
            if (distance < requiredDistance) {
                configuration = configs[i];
                requiredDistance = distance;
            }
        }

        if (configuration == null) {
            source.sendError(Text.of("Your target is too far away!"));
            return false;
        }
        source.sendFeedback(Text.of(String.format("§aConfiguration: %s; distance: %.2f", configuration, Math.sqrt(requiredDistance))));
        return true;
    }

    private static int setOrigin(CommandContext<FabricClientCommandSource> context) {
        origin = getPosFromArgument(context.getArgument("pos", DefaultPosArgument.class), context.getSource());
        direction = Direction.byName(context.getArgument("direction", String.class));
        context.getSource().sendFeedback(Text.of("§oOrigin set to " + origin.toShortString() + " facing " + direction.asString()));
        return 1;
    }

    private static int originFromPlayerPos(CommandContext<FabricClientCommandSource> context) {
        final var player = context.getSource().getPlayer();
        origin = player.getBlockPos();
        direction = player.getHorizontalFacing();
        context.getSource().sendFeedback(Text.of("§oOrigin set to " + origin.toShortString() + " facing " + direction.asString()));
        return 1;
    }

    private static BlockPos getPosFromArgument(DefaultPosArgument argument, FabricClientCommandSource source) {
        DefaultPosArgumentAccessor accessor = (DefaultPosArgumentAccessor) argument;
        Vec3d pos = source.getPlayer().getPos();
        return new BlockPos(
                accessor.getX().toAbsoluteCoordinate(pos.x),
                accessor.getY().toAbsoluteCoordinate(pos.y),
                accessor.getZ().toAbsoluteCoordinate(pos.z)
        );
    }
}
