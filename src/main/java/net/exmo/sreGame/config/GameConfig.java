package net.exmo.sreGame.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.SreGame;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class GameConfig {
   private final Path file;
   private final Map<String, String> values = new LinkedHashMap<>();

   public GameConfig(Path configDir) {
      this.file = configDir.resolve("config.yml");
   }

   public void load() {
      this.values.clear();
      this.putDefaults();
      try {
         Files.createDirectories(this.file.getParent());
         if (!Files.exists(this.file)) {
            Files.writeString(this.file, this.dump(), StandardCharsets.UTF_8);
            return;
         }
         for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
               continue;
            }
            int split = trimmed.indexOf(':');
            if (split <= 0) {
               continue;
            }
            String key = trimmed.substring(0, split).trim();
            String value = trimmed.substring(split + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
               value = value.substring(1, value.length() - 1);
            }
            this.values.put(key, value);
         }
         this.migrateLegacySizes();
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to load config.yml, using defaults", e);
      }
   }

   private void putDefaults() {
      this.values.put("build-war.world", "minecraft:overworld");
      this.values.put("build-war.origin-x", "20000");
      this.values.put("build-war.origin-y", "64");
      this.values.put("build-war.origin-z", "0");
      this.values.put("build-war.plot-size", "42");
      this.values.put("build-war.gap", "64");
      this.values.put("build-war.height", "64");
      this.values.put("build-war.pregen", "20");
      this.values.put("fake-human.origin-x", "20000");
      this.values.put("fake-human.origin-z", "8000");
      this.values.put("fake-human.pregen", "4");
      this.values.put("chicken-horse.origin-x", "20000");
      this.values.put("chicken-horse.origin-z", "16000");
      this.values.put("chicken-horse.pregen", "4");
      this.values.put("lucky-pillar.origin-x", "20000");
      this.values.put("lucky-pillar.origin-z", "24000");
      this.values.put("lucky-pillar.pregen", "4");
      this.values.put("dont-do.origin-x", "20000");
      this.values.put("dont-do.origin-z", "32000");
      this.values.put("pillar-pummel.origin-x", "20000");
      this.values.put("pillar-pummel.origin-z", "40000");
      this.values.put("pillar-pummel.pregen", "4");
   }

   private String dump() {
      StringBuilder sb = new StringBuilder();
      sb.append("# SRE-GAME\n");
      for (Map.Entry<String, String> entry : this.values.entrySet()) {
         sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
      }
      return sb.toString();
   }

   private void save() {
      try {
         Files.createDirectories(this.file.getParent());
         Files.writeString(this.file, this.dump(), StandardCharsets.UTF_8);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to save config.yml", e);
      }
   }

   private void migrateLegacySizes() {
      if (this.getInt("build-war.plot-size", 42) == 21
         && this.getInt("build-war.gap", 64) == 32
         && this.getInt("build-war.height", 64) == 32) {
         this.values.put("build-war.plot-size", "42");
         this.values.put("build-war.gap", "64");
         this.values.put("build-war.height", "64");
         this.save();
      }
   }

   public String getString(String key, String def) {
      String value = this.values.get(key);
      return value == null || value.isBlank() ? def : value;
   }

   public int getInt(String key, int def) {
      try {
         return Integer.parseInt(this.getString(key, String.valueOf(def)));
      } catch (NumberFormatException e) {
         return def;
      }
   }

   public ServerLevel world(MinecraftServer server) {
      if (server == null) {
         return null;
      }
      String raw = this.getString("build-war.world", "minecraft:overworld");
      ResourceLocation loc = ResourceLocation.tryParse(raw);
      if (loc == null) {
         return server.overworld();
      }
      ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, loc));
      return level != null ? level : server.overworld();
   }

   public int originX() {
      return this.getInt("build-war.origin-x", 20000);
   }

   public int originY() {
      return this.getInt("build-war.origin-y", 64);
   }

   public int originZ() {
      return this.getInt("build-war.origin-z", 0);
   }

   public int plotSize() {
      return Math.max(7, this.getInt("build-war.plot-size", 42));
   }

   public int gap() {
      return Math.max(16, this.getInt("build-war.gap", 64));
   }

   public int height() {
      return Math.max(16, this.getInt("build-war.height", 64));
   }

   public int pregen() {
      return Math.max(3, Math.min(20, this.getInt("build-war.pregen", 20)));
   }

   public int fakeHumanOriginX() {
      return this.getInt("fake-human.origin-x", 20000);
   }

   public int fakeHumanOriginZ() {
      return this.getInt("fake-human.origin-z", 8000);
   }

   public int fakeHumanPregen() {
      return Math.max(1, Math.min(8, this.getInt("fake-human.pregen", 4)));
   }

   public int chickenHorseOriginX() {
      return this.getInt("chicken-horse.origin-x", 20000);
   }

   public int chickenHorseOriginZ() {
      return this.getInt("chicken-horse.origin-z", 16000);
   }

   public int chickenHorsePregen() {
      return Math.max(1, Math.min(8, this.getInt("chicken-horse.pregen", 4)));
   }

   public int luckyPillarOriginX() {
      return this.getInt("lucky-pillar.origin-x", 20000);
   }

   public int luckyPillarOriginZ() {
      return this.getInt("lucky-pillar.origin-z", 24000);
   }

   public int luckyPillarPregen() {
      return Math.max(1, Math.min(8, this.getInt("lucky-pillar.pregen", 4)));
   }

   public int dontDoOriginX() {
      return this.getInt("dont-do.origin-x", 20000);
   }

   public int dontDoOriginZ() {
      return this.getInt("dont-do.origin-z", 32000);
   }

   public int pillarPummelOriginX() {
      return this.getInt("pillar-pummel.origin-x", 20000);
   }

   public int pillarPummelOriginZ() {
      return this.getInt("pillar-pummel.origin-z", 40000);
   }

   public int pillarPummelPregen() {
      return Math.max(1, Math.min(8, this.getInt("pillar-pummel.pregen", 4)));
   }
}
