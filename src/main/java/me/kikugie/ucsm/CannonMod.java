package me.kikugie.ucsm;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

public class CannonMod implements ModInitializer {
    public static final Logger LOGGER = Logger.getLogger("ucsm");
    public static Vec3d[] points;
    public static String[] configs;
    @Override
    public void onInitialize() {
        ClientCommandRegistrationCallback.EVENT.register(Command::register);
        ClientPlayConnectionEvents.JOIN.register(((handler, sender, client) -> initConfig()));
        initConfig();
    }

    public static boolean initConfig() {
        var configDir = new File(MinecraftClient.getInstance().runDirectory, "config/ucsm/");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        try {
            points = Arrays.stream(readList(new File(configDir, "Pt.txt"))).map(val -> {
                var k = val.split(",");
                return new Vec3d(Float.parseFloat(k[0]), Float.parseFloat(k[1]), Float.parseFloat(k[2]));
            }).toArray(Vec3d[]::new);
            configs = readList(new File(configDir, "Ct.txt"));
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
}
