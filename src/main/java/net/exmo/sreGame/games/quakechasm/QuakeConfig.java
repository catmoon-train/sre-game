package net.exmo.sreGame.games.quakechasm;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Quakechasm configuration. Ported from PluginConfig, adapted to Fabric's config dir.
 * Serialised as JSON via Gson.
 */
public final class QuakeConfig {
    public Locale locale = new Locale();
    public Lobby lobby = new Lobby();
    public PlayerConfig player = new PlayerConfig();

    public static final class Locale {
        public String fallback = "zh_CN";
    }

    public static final class Lobby {
        public String world = "world";
        public double x = 0;
        public double y = 64;
        public double z = 0;
        public float yaw = 0;
        public float pitch = 0;
    }

    public static final class PlayerConfig {
        public float walkSpeed = 0.4f;
    }

    private static volatile QuakeConfig instance;

    public static QuakeConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static QuakeConfig reload() {
        instance = load();
        return instance;
    }

    private static QuakeConfig load() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("sre-game");
        Path file = dir.resolve("quakechasm.json");
        try {
            Files.createDirectories(dir);
            if (!Files.exists(file)) {
                QuakeConfig def = new QuakeConfig();
                Files.writeString(file, new Gson().toJson(def));
                return def;
            }
            String json = Files.readString(file);
            QuakeConfig cfg = new Gson().fromJson(json, QuakeConfig.class);
            return cfg == null ? new QuakeConfig() : cfg;
        } catch (IOException e) {
            return new QuakeConfig();
        }
    }
}
