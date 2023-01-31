package me.kikugie.ucsm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.kikugie.ucsm.CannonMod.*;
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
                        .then(argument("range", IntegerArgumentType.integer(1, 9000))
                                .executes(Command::setTntRange)))
                .then(literal("origin")
                        .executes(Command::originFromPlayerPos)
                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                .then(argument("direction", new DirectionArgumentType())
                                        .executes(Command::setOrigin))))
                .then(literal("target")
                        .executes(Command::raycastTarget)
                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                .executes(Command::posTarget)))
                .then(literal("pack")
                        .executes(Command::pack)));
    }


    private static int help(CommandContext<FabricClientCommandSource> context) {
        context.getSource().sendFeedback(Text.of("""
                §oCommand functionality:
                  - /ucsm reload: reload config files.
                  - /ucsm precision <int>: maximum distance to the explosion.
                  - /ucsm origin [<pos> <direction>]: set cannon origin to a location, uses player position and facing direction if no arguments provided.
                  - /ucsm target [<pos>]: output closest configuration to specified position. Uses block player is looking at (even very far) if no argument is provided.
                  - /ucsm pack: Packs Ct.txt and Pt.txt into a binary format to reduce file size."""

        ));
        return 0;
    }

    private static int setTntRange(CommandContext<FabricClientCommandSource> context) {
        int range = context.getArgument("range", Integer.class);
        sqTntRange = range * range;
        context.getSource().sendFeedback(Text.of("§oPrecision set to " + range));
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
        if (kdTree.isEmpty()) {
            source.sendError(Text.of("Config is not loaded!"));
            return false;
        }
        if (origin == null || direction == null) {
            source.sendError(Text.of("Origin is not set up!"));
            return false;
        }

        // God forgive me
        double rotate = switch (direction) {
            case SOUTH -> Math.PI;
            case EAST -> Math.PI * 0.5;
            case WEST -> Math.PI * -0.5;
            default -> 0;
        };

        Vec3d target = Vec3d.of(pos)
                .subtract(origin.getX() - 0.5, origin.getY() - 0.5, origin.getZ() - 0.5)
                .rotateY((float) rotate);

        var result = kdTree.nearestNeighbour(target);
        if (result == null) {
            source.sendError(Text.of("Your target is too far away!"));
            return false;
        }

        source.sendFeedback(Text.of(String.format("§aConfiguration: %s; distance: %.2f", result.payload, Math.sqrt(result.distance))));
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

    private static int pack(CommandContext<FabricClientCommandSource> context) {
        var cFile = new File(configDir, "Ct.txt");
        var pFile = new File(configDir, "Pt.txt");
        var packedFile = new File(configDir, "packed.bin");

        try (BufferedReader c = new BufferedReader(new FileReader(cFile));
             BufferedReader p = new BufferedReader(new FileReader(pFile));
             FileOutputStream fos = new FileOutputStream(packedFile.getAbsolutePath())) {

            Map<Byte, List<Byte>> map = new HashMap<>();
            String line;

            while ((line = c.readLine()) != null) {
                String[] split = line.split(",");
                byte tnt = Byte.parseByte(split[3]);

                String[] pSplit = p.readLine().split(",");
                ByteBuffer buffer = ByteBuffer.allocate(24);
                for (int i = 0; i < 3; ++i) {
                    buffer.putDouble(i * 8, Double.parseDouble(pSplit[i]));
                }

                List<Byte> list = Arrays.stream(split).limit(3).map(Byte::parseByte).collect(Collectors.toList());
                for (byte b : buffer.array()) {
                    list.add(b);
                }

                if (map.containsKey(tnt)) {
                    map.get(tnt).addAll(list);
                    continue;
                }
                map.put(tnt, list);
            }

            for (Map.Entry<Byte, List<Byte>> entry : map.entrySet()) {
                Byte k = entry.getKey();
                List<Byte> v = entry.getValue();
                byte[] a = new byte[5 + v.size()];
                a[0] = k;
                ByteBuffer b = ByteBuffer.allocate(4);
                b.putInt(v.size());
                for (int i = 0; i < 4; ++i) {
                    a[i + 1] = b.get(i);
                }

                for (int i = 0; i < v.size(); ++i) {
                    a[i + 5] = v.get(i);
                }
                fos.write(a);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 1;
        }

        var originalSize = cFile.length() + pFile.length();
        context.getSource().sendFeedback(Text.of(String.format("§oPacked successfully! Packed size: %d bytes (%.2f%% of original size)", packedFile.length(), packedFile.length() * 100D / originalSize)));

        return 1;
    }
}
