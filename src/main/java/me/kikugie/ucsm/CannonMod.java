package me.kikugie.ucsm;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class CannonMod implements ModInitializer {
    public static final Logger LOGGER = Logger.getLogger("ucsm");
    public static Map<Vec3d, String> cannonData = new HashMap<>();
    @Override
    public void onInitialize() {
        ClientCommandRegistrationCallback.EVENT.register(Command::register);
        try {
            initConfig();
        } catch (FileNotFoundException e) {

        }
    }

    public static void initConfig() throws FileNotFoundException {
        var configDir = new File(MinecraftClient.getInstance().runDirectory, "config/ucsm/");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        try {
            var points = readList(new File(configDir, "Pt.txt"));
            var setups = readList(new File(configDir, "Ct.txt"));

            cannonData.clear();
            for (int i = 0; i < points.length; i++) {
                var k = points[i].split(",");
                var v = setups[i];
                cannonData.put(new Vec3d(Float.parseFloat(k[0]), Float.parseFloat(k[1]), Float.parseFloat(k[2])), v);
            }
        } catch (IOException e) {
            LOGGER.warning("Error reading config files");
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
