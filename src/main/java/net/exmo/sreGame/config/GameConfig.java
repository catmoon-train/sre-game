package net.exmo.sreGame.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.SreGame;
import net.exmo.sreGame.games.partygames.PartyGameType;
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
      this.values.put("server.hide-join-leave-notifications", "false");
      // The dimension used for minigames. Its player inventory is kept separate
      // from minecraft:overworld (the hub/survival inventory).
      this.values.put("inventory.game-world", "minecraft:game");
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
      this.values.put("dodgeball.origin-x", "20000");
      this.values.put("dodgeball.origin-z", "48000");
      this.values.put("dodgeball.pregen", "4");
      this.values.put("football.origin-x", "20000");
      this.values.put("football.origin-z", "52000");
      this.values.put("football.pregen", "4");
      this.values.put("dig-to-death.origin-x", "20000");
      this.values.put("dig-to-death.origin-z", "56000");
      this.values.put("dig-to-death.pregen", "4");
      this.values.put("you-build-run.origin-x", "20000");
      this.values.put("you-build-run.origin-z", "64000");
      this.values.put("you-build-run.pregen", "4");
      this.values.put("parkour.origin-x", "20000");
      this.values.put("parkour.origin-z", "72000");
      this.values.put("push-the-button.origin-x", "20000");
      this.values.put("push-the-button.origin-z", "80000");
      this.values.put("push-the-button.pregen", "2");
      this.values.put("skyworld.origin-x", "20000");
      this.values.put("skyworld.origin-z", "88000");
      this.values.put("skyworld.pregen", "4");
      this.values.put("name-tag-war.origin-x", "20000");
      this.values.put("name-tag-war.origin-z", "96000");
      this.values.put("name-tag-war.pregen", "4");
      this.values.put("fill-in-the-wall.origin-x", "20000");
      this.values.put("fill-in-the-wall.origin-z", "104000");
      this.values.put("fill-in-the-wall.pregen", "4");
      this.values.put("rhythm.origin-x", "10000");
      this.values.put("rhythm.origin-y", "200");
      this.values.put("rhythm.origin-z", "100000");
      this.values.put("party-games.world", "minecraft:overworld");
      this.values.put("party-games.origin-x", "20000");
      this.values.put("party-games.origin-z", "112000");
      this.values.put("party-games.blocks-per-tick", "9000");
      this.values.put("hypixel-says.world", "minecraft:overworld");
      this.values.put("hypixel-says.origin-x", "20000");
      this.values.put("hypixel-says.origin-z", "120000");
      this.values.put("hypixel-says.pregen", "4");
      this.values.put("blocked-combat.origin-x", "20000");
      this.values.put("blocked-combat.origin-z", "140000");
      this.values.put("tunnel-rats.origin-x", "20000");
      this.values.put("tunnel-rats.origin-z", "148000");
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

   /** Whether this is the dedicated minigame dimension whose inventory is isolated. */
   public boolean isGameWorld(ServerLevel level) {
      if (level == null) {
         return false;
      }
      ResourceLocation gameWorld = ResourceLocation.tryParse(this.getString("inventory.game-world", "minecraft:game"));
      return gameWorld != null && level.dimension().location().equals(gameWorld);
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

   public int dodgeballOriginX() {
      return this.getInt("dodgeball.origin-x", 20000);
   }

   public int dodgeballOriginZ() {
      return this.getInt("dodgeball.origin-z", 48000);
   }

   public int dodgeballPregen() {
      return Math.max(1, Math.min(8, this.getInt("dodgeball.pregen", 4)));
   }

   public int footballOriginX() {
      return this.getInt("football.origin-x", 20000);
   }

   public int footballOriginZ() {
      return this.getInt("football.origin-z", 52000);
   }

   public int footballPregen() {
      return Math.max(1, Math.min(8, this.getInt("football.pregen", 4)));
   }

   public int digToDeathOriginX() {
      return this.getInt("dig-to-death.origin-x", 20000);
   }

   public int digToDeathOriginZ() {
      return this.getInt("dig-to-death.origin-z", 56000);
   }

   public int digToDeathPregen() {
      return Math.max(1, Math.min(8, this.getInt("dig-to-death.pregen", 4)));
   }

   public int youBuildRunOriginX() {
      return this.getInt("you-build-run.origin-x", 20000);
   }

   public int youBuildRunOriginZ() {
      return this.getInt("you-build-run.origin-z", 64000);
   }

   public int youBuildRunPregen() {
      return Math.max(1, Math.min(8, this.getInt("you-build-run.pregen", 4)));
   }

   public int parkourOriginX() {
      return this.getInt("parkour.origin-x", 20000);
   }

   public int parkourOriginZ() {
      return this.getInt("parkour.origin-z", 72000);
   }

   public int pushTheButtonOriginX() {
      return this.getInt("push-the-button.origin-x", 20000);
   }

   public int pushTheButtonOriginZ() {
      return this.getInt("push-the-button.origin-z", 80000);
   }

   public int pushTheButtonPregen() {
      return Math.max(1, Math.min(8, this.getInt("push-the-button.pregen", 2)));
   }

   public int skyWorldOriginX() {
      return this.getInt("skyworld.origin-x", 20000);
   }

   public int skyWorldOriginZ() {
      return this.getInt("skyworld.origin-z", 88000);
   }

   public int skyWorldPregen() {
      return Math.max(1, Math.min(8, this.getInt("skyworld.pregen", 4)));
   }

   public int nameTagWarOriginX() {
      return this.getInt("name-tag-war.origin-x", 20000);
   }

   public int nameTagWarOriginZ() {
      return this.getInt("name-tag-war.origin-z", 96000);
   }

   public int nameTagWarPregen() {
      return Math.max(1, Math.min(8, this.getInt("name-tag-war.pregen", 4)));
   }

   public int fillInTheWallOriginX() {
      return this.getInt("fill-in-the-wall.origin-x", 20000);
   }

   public int fillInTheWallOriginZ() {
      return this.getInt("fill-in-the-wall.origin-z", 104000);
   }

   public int fillInTheWallPregen() {
      return Math.max(1, Math.min(8, this.getInt("fill-in-the-wall.pregen", 4)));
   }

   public int rhythmOriginX() {
      return this.getInt("rhythm.origin-x", 10000);
   }

   public int rhythmOriginY() {
      return this.getInt("rhythm.origin-y", 200);
   }

   public int rhythmOriginZ() {
      return this.getInt("rhythm.origin-z", 100000);
   }

   public ServerLevel partyGamesWorld(MinecraftServer server) {
      if (server == null) return null;
      String raw = this.getString("party-games.world", this.getString("build-war.world", "minecraft:overworld"));
      ResourceLocation loc = ResourceLocation.tryParse(raw);
      if (loc == null) return this.world(server);
      ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, loc));
      return level == null ? this.world(server) : level;
   }

   public int partyGamesOriginX() { return this.getInt("party-games.origin-x", 20000); }
   public int partyGamesOriginZ() { return this.getInt("party-games.origin-z", 112000); }
   public int partyGamesBlocksPerTick() { return Math.max(1000, Math.min(30000, this.getInt("party-games.blocks-per-tick", 9000))); }
   /** Global switch used by the OP party-game catalogue. Defaults to enabled so upgrades do not hide existing games. */
   public boolean partyGameEnabled(PartyGameType type) {
      return Boolean.parseBoolean(this.getString("party-games.enabled." + type.id(), "true"));
   }

   public void setPartyGameEnabled(PartyGameType type,
                                     boolean enabled) {
      this.values.put("party-games.enabled." + type.id(), String.valueOf(enabled));
      this.save();
   }

   /** Generic per-game enable switch covering every registered MiniGame, not just party games. Defaults to enabled. */
   public boolean isGameEnabled(String id) {
      return Boolean.parseBoolean(this.getString("games.enabled." + id, "true"));
   }

   public void setGameEnabled(String id, boolean enabled) {
      this.values.put("games.enabled." + id, String.valueOf(enabled));
      this.save();
   }

   public ServerLevel hypixelSaysWorld(MinecraftServer server) {
      if (server == null) return null;
      String raw = this.getString("hypixel-says.world", "minecraft:overworld");
      ResourceLocation loc = ResourceLocation.tryParse(raw);
      if (loc == null) return this.world(server);
      ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, loc));
      return level == null ? this.world(server) : level;
   }

   public int hypixelSaysOriginX() { return this.getInt("hypixel-says.origin-x", 20000); }
   public int hypixelSaysOriginZ() { return this.getInt("hypixel-says.origin-z", 120000); }
   public int hypixelSaysPregen() { return Math.max(1, Math.min(8, this.getInt("hypixel-says.pregen", 4))); }

   public int blockedCombatOriginX() { return this.getInt("blocked-combat.origin-x", 20000); }
   public int blockedCombatOriginZ() { return this.getInt("blocked-combat.origin-z", 140000); }

   public int tunnelRatsOriginX() { return this.getInt("tunnel-rats.origin-x", 20000); }
   public int tunnelRatsOriginZ() { return this.getInt("tunnel-rats.origin-z", 148000); }

   public boolean hideJoinLeaveNotifications() {
      return Boolean.parseBoolean(this.getString("server.hide-join-leave-notifications", "false"));
   }

   public void setHideJoinLeaveNotifications(boolean hide) {
      this.values.put("server.hide-join-leave-notifications", String.valueOf(hide));
      this.save();
   }
}
