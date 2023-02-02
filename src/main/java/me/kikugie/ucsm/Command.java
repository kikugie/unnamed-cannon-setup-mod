package me.kikugie.ucsm;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import jk.tree.KDTree;
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
import java.util.zip.GZIPOutputStream;

import static me.kikugie.ucsm.CannonMod.configDir;
import static me.kikugie.ucsm.CannonMod.kdTree;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class Command {
    private static int distanceLimit = 20;
    private static Vec3i origin = null;
    private static Direction direction = null;
    private static boolean mirrored = false;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess ignoredAccess) {
        dispatcher.register(literal("ucsm")
                .executes(Command::help)
                .then(literal("reload")
                        .executes(Command::reload))
                .then(literal("precision")
                        .then(argument("range", IntegerArgumentType.integer(1, 9000))
                                .executes(Command::setTntRange)))
                .then(literal("origin")
                        .executes(context -> originFromPlayerPos(context, false))
                        .then(literal("mirrored")
                                .executes(context -> originFromPlayerPos(context, true)))
                        .then(argument("pos", BlockPosArgumentType.blockPos())
                                .then(argument("direction", new DirectionArgumentType())
                                        .executes(context -> originFromPos(context, false))
                                        .then(literal("mirrored")
                                                .executes(context -> originFromPos(context, true))))))
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
                  - /ucsm origin [<pos> <direction>] [mirrored]: set cannon origin to a location, uses player position and facing direction if no arguments provided.
                  - /ucsm target [<pos>]: output closest configuration to specified position. Uses block player is looking at (even very far) if no argument is provided.
                  - /ucsm pack: Packs Ct.txt and Pt.txt into a binary format to reduce file size."""

        ));
        return 0;
    }

    private static int setTntRange(CommandContext<FabricClientCommandSource> context) {
        distanceLimit = context.getArgument("range", Integer.class);
        context.getSource().sendFeedback(Text.of("§oPrecision set to " + distanceLimit));
        return 1;
    }

    private static int reload(CommandContext<FabricClientCommandSource> context) {
        origin = null;
        direction = null;
        mirrored = false;
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
                        player.getEyePos()
                                .add(player.getRotationVecClient()
                                        .multiply(1000f)),
                        RaycastContext.ShapeType.OUTLINE,
                        RaycastContext.FluidHandling.ANY,
                        player
                )
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
        Vec3d target = Vec3d.of(pos)
                .subtract(origin.getX() - 0.5, origin.getY() - 0.5, origin.getZ() - 0.5) // Aim at the center of the block
                .rotateY((float) Math.toRadians(direction.asRotation() + 180));
        if (mirrored) {
            target = new Vec3d(-target.x, target.y, target.z);
        }

        KDTree.SearchResult<String> result = kdTree.nearestNeighbour(target);
        if (result.distance > distanceLimit) {
            source.sendError(Text.of("Your target is too far away!"));
            return false;
        }

        source.sendFeedback(
                Text.of(String.format("§aConfiguration: %s; distance: %.2f", result.payload, Math.sqrt(result.distance))));
        return true;
    }

    private static int originFromPos(CommandContext<FabricClientCommandSource> context, boolean mirror) {
        setOrigin(
                context,
                getPosFromArgument(context.getArgument("pos", DefaultPosArgument.class), context.getSource()),
                Direction.byName(context.getArgument("direction", String.class)),
                mirror
        );
        return 1;
    }

    private static int originFromPlayerPos(CommandContext<FabricClientCommandSource> context, boolean mirror) {
        final var player = context.getSource().getPlayer();
        setOrigin(context, player.getBlockPos(), player.getHorizontalFacing(), mirror);
        return 1;
    }

    private static void setOrigin(CommandContext<FabricClientCommandSource> context, Vec3i pos, Direction dir, boolean mirror) {
        origin = pos;
        direction = dir;
        mirrored = mirror;
        String output = "§oOrigin set to " + origin.toShortString() + " facing " + direction.asString();
        if (mirror) {
            context.getSource().sendFeedback(Text.of(output + " (mirrored)"));
            return;
        }
        context.getSource().sendFeedback(Text.of(output));
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
        var configFile = new File(configDir, "Ct.txt");
        var pointsFile = new File(configDir, "Pt.txt");
        var packedFile = new File(configDir, "packed.bin");
        if (packedFile.exists() && !configFile.exists() && !pointsFile.exists()) {
            context.getSource().sendError(Text.of("Packed file already exists, nothing to repack."));
            return 0;
        }
        if (!configFile.exists() || !pointsFile.exists()) {
            context.getSource().sendError(Text.of("Missing files for packing!"));
            return 0;
        }

        try (BufferedReader configReader = new BufferedReader(new FileReader(configFile));
             BufferedReader pointsReader = new BufferedReader(new FileReader(pointsFile));
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(packedFile.getAbsolutePath()))) {

            Map<Byte, List<Byte>> readBytes = new HashMap<>();
            String line;

            while ((line = configReader.readLine()) != null) {
                String[] splitConfig = line.split(",");
                byte tnt = Byte.parseByte(splitConfig[3]);

                String[] splitPoints = pointsReader.readLine().split(",");
                ByteBuffer buffer = ByteBuffer.allocate(24);
                for (int i = 0; i < 3; ++i) {
                    buffer.putDouble(i * 8, Double.parseDouble(splitPoints[i]));
                }

                List<Byte> list = Arrays.stream(splitConfig).limit(3).map(Byte::parseByte).collect(Collectors.toList());
                for (byte b : buffer.array()) {
                    list.add(b);
                }

                if (readBytes.containsKey(tnt)) {
                    readBytes.get(tnt).addAll(list);
                    continue;
                }
                readBytes.put(tnt, list);
            }

            for (Map.Entry<Byte, List<Byte>> entry : readBytes.entrySet()) {
                Byte entryKey = entry.getKey();
                List<Byte> entryValue = entry.getValue();
                byte[] encodedBytes = new byte[5 + entryValue.size()];
                encodedBytes[0] = entryKey;
                ByteBuffer byteBuffer = ByteBuffer.allocate(4);
                byteBuffer.putInt(entryValue.size());
                for (int i = 0; i < 4; ++i) {
                    encodedBytes[i + 1] = byteBuffer.get(i);
                }

                for (int i = 0; i < entryValue.size(); ++i) {
                    encodedBytes[i + 5] = entryValue.get(i);
                }
                gzipOutputStream.write(encodedBytes);
                configFile.deleteOnExit();
                pointsFile.deleteOnExit();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return 1;
        }

        var originalSize = configFile.length() + pointsFile.length();
        context.getSource().sendFeedback(Text.of(String.format("§oPacked successfully! Packed size: %d bytes (%.2f%% of original size).\nOld files will be deleted on game exit.", packedFile.length(), packedFile.length() * 100D / originalSize)));

        return 1;
    }
}
