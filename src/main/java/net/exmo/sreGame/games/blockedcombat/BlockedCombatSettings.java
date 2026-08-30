package net.exmo.sreGame.games.blockedcombat;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

/** Per-room rules for the mining PvP mode. */
public final class BlockedCombatSettings {
   private static final int[] TEAM_SIZES = {1, 2, 3, 4, 5, 6};
   private static final int[] DEATH_LIMITS = {1, 3, 5, 7, 10};
   private static final int[] ARENA_SIZES = {48, 60, 72, 84};
   private static final int[] TNT_SCARCITIES = {25, 40, 50, 60};
   private static final int[] SPAWN_SPREADS = {1, 3, 5};
   private static final int[] PREPARE_SECONDS = {5, 10, 15};

   private int teamSize = 2;
   private int deathLimit = 3;
   private int arenaSize = 60;
   /** Percentage of generated TNT that is replaced by glass. */
   private int tntScarcity = 40;
   private int spawnSpread = 3;
   private int prepareSeconds = 10;
   private boolean friendlyFire;
   private boolean richStarterKit;

   public int teamSize() { return this.teamSize; }
   public int deathLimit() { return this.deathLimit; }
   public int arenaSize() { return this.arenaSize; }
   public int tntScarcity() { return this.tntScarcity; }
   public int spawnSpread() { return this.spawnSpread; }
   public boolean friendlyFire() { return this.friendlyFire; }
   public int prepareSeconds() { return this.prepareSeconds; }
   public int prepareTicks() { return this.prepareSeconds * 20; }
   public boolean richStarterKit() { return this.richStarterKit; }

   public void cycleTeamSize() { this.teamSize = next(TEAM_SIZES, this.teamSize, 2); }
   public void cycleDeathLimit() { this.deathLimit = next(DEATH_LIMITS, this.deathLimit, 3); }
   public void cycleArenaSize() { this.arenaSize = next(ARENA_SIZES, this.arenaSize, 60); }
   public void cycleTntScarcity() { this.tntScarcity = next(TNT_SCARCITIES, this.tntScarcity, 40); }
   public void cycleSpawnSpread() { this.spawnSpread = next(SPAWN_SPREADS, this.spawnSpread, 3); }
   public void cyclePrepareSeconds() { this.prepareSeconds = next(PREPARE_SECONDS, this.prepareSeconds, 10); }
   public void toggleFriendlyFire() { this.friendlyFire = !this.friendlyFire; }
   public void toggleRichStarterKit() { this.richStarterKit = !this.richStarterKit; }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("teamSize", this.teamSize);
      data.put("deathLimit", this.deathLimit);
      data.put("arenaSize", this.arenaSize);
      data.put("tntScarcity", this.tntScarcity);
      data.put("spawnSpread", this.spawnSpread);
      data.put("prepareSeconds", this.prepareSeconds);
      data.put("friendlyFire", this.friendlyFire);
      data.put("richStarterKit", this.richStarterKit);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) return;
      this.teamSize = valid(TEAM_SIZES, SettingsIo.asInt(data, "teamSize", this.teamSize), 2);
      this.deathLimit = valid(DEATH_LIMITS, SettingsIo.asInt(data, "deathLimit", this.deathLimit), 3);
      this.arenaSize = valid(ARENA_SIZES, SettingsIo.asInt(data, "arenaSize", this.arenaSize), 60);
      this.tntScarcity = valid(TNT_SCARCITIES, SettingsIo.asInt(data, "tntScarcity", this.tntScarcity), 40);
      this.spawnSpread = valid(SPAWN_SPREADS, SettingsIo.asInt(data, "spawnSpread", this.spawnSpread), 3);
      this.prepareSeconds = valid(PREPARE_SECONDS, SettingsIo.asInt(data, "prepareSeconds", this.prepareSeconds), 10);
      this.friendlyFire = SettingsIo.asBool(data, "friendlyFire", this.friendlyFire);
      this.richStarterKit = SettingsIo.asBool(data, "richStarterKit", this.richStarterKit);
   }

   private static int next(int[] values, int current, int fallback) {
      for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
      return fallback;
   }

   private static int valid(int[] values, int value, int fallback) {
      for (int candidate : values) if (candidate == value) return value;
      return fallback;
   }
}
