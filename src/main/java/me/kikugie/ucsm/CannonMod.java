package me.kikugie.ucsm;

import jk.tree.KDTree;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

public class CannonMod implements ModInitializer {
    public static final Logger LOGGER = Logger.getLogger("ucsm");
    public static KDTree<String> kdTree;
    public static final File configDir = new File(MinecraftClient.getInstance().runDirectory, "config/ucsm/");

    public static boolean initConfig() {
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        try {
            kdTree = new KDTree<>();
            var packed = new File(configDir, "packed.bin");

            if (packed.exists() && packed.canRead()) {
                var bytes = Files.readAllBytes(new File(configDir, "packed.bin").toPath());
                var idx = 0;

                // Binary format:
                // [propellant{1}number_of_ps{4}[x{8}y{8}z{8}first{1}second{1}third{1}]+]*
                while (idx < bytes.length) {
                    // propellant{1}
                    var tnt = bytes[idx++];

                    // number_of_ps{4}
                    byte[] lenBytes = new byte[4];
                    System.arraycopy(bytes, idx, lenBytes, 0, 4);
                    int len = ByteBuffer.wrap(lenBytes).order(ByteOrder.BIG_ENDIAN).getInt();
                    idx += 4;

                    // [x{8}y{8}z{8}first{1}second{1}third{1}]+
                    for (int i = idx, lim = idx + len; i < lim; i += 27) {
                        byte[] chunk = new byte[27];
                        System.arraycopy(bytes, i, chunk, 0, 27);

                        byte[] xBytes = new byte[8];
                        System.arraycopy(chunk, 3, xBytes, 0, 8);
                        double x = ByteBuffer.wrap(xBytes).order(ByteOrder.BIG_ENDIAN).getDouble();

                        byte[] yBytes = new byte[8];
                        System.arraycopy(chunk, 11, yBytes, 0, 8);
                        double y = ByteBuffer.wrap(yBytes).order(ByteOrder.BIG_ENDIAN).getDouble();

                        byte[] zBytes = new byte[8];
                        System.arraycopy(chunk, 19, zBytes, 0, 8);
                        double z = ByteBuffer.wrap(zBytes).order(ByteOrder.BIG_ENDIAN).getDouble();

                        kdTree.addPoint(new double[] { x, y, z }, chunk[0] + "," + chunk[1] + "," + chunk[2] + "," + tnt);
                    }

                    idx += len;
                }
            } else {
                var points = Arrays.stream(readList(new File(configDir, "Pt.txt"))).map(val -> {
                    var k = val.split(",");
                    return new double[] { Float.parseFloat(k[0]), Float.parseFloat(k[1]), Float.parseFloat(k[2]) };
                }).toArray(double[][]::new);
                var configs = readList(new File(configDir, "Ct.txt"));

                for (int i = 0, lim = points.length; i < lim; i++) {
                    kdTree.addPoint(points[i], configs[i]);
                }
            }

            return true;
        } catch (IOException e) {
            LOGGER.warning("Error reading config files");
            return false;
        }
    }

    private static String[] readList(File file) throws IOException {
        var bf = new BufferedReader(new FileReader(file));
        var list = new ArrayList<String>();
        String line = bf.readLine();

        while (line != null) {
            list.add(line);
            line = bf.readLine();
        }

        return list.toArray(new String[0]);
    }

    @Override
    public void onInitialize() {
        ClientCommandRegistrationCallback.EVENT.register(Command::register);
        ClientPlayConnectionEvents.JOIN.register(((handler, sender, client) -> initConfig()));
        initConfig();
    }
}
