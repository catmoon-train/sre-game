package net.exmo.sreGame.games.tunnelrats;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

/** Per-room rules for the Tunnel Rats port. */
public final class TunnelRatsSettings {
   private static final int[] ARENA_LENGTHS = {56, 72, 88};
   private static final int[] COUNTDOWN_SECONDS = {5, 10, 15};
   private static final int[] RESPAWN_SECONDS = {3, 5, 8};
   private static final int[] LAST_STAND_SECONDS = {0, 180, 300};

   private int arenaLength = 72;
   private int countdownSeconds = 5;
   private int respawnSeconds = 5;
   private int lastStandSeconds = 300;
   private boolean friendlyFire;
   private boolean nightVision;
   private boolean speed;
   private boolean haste;
   private boolean teamArmor = true;

   public int arenaLength() { return this.arenaLength; }
   public void cycleArenaLength() { this.arenaLength = next(ARENA_LENGTHS, this.arenaLength, 72); }
   public int countdownSeconds() { return this.countdownSeconds; }
   public int countdownTicks() { return this.countdownSeconds * 20; }
   public void cycleCountdownSeconds() { this.countdownSeconds = next(COUNTDOWN_SECONDS, this.countdownSeconds, 5); }
   public int respawnSeconds() { return this.respawnSeconds; }
   public int respawnTicks() { return this.respawnSeconds * 20; }
   public void cycleRespawnSeconds() { this.respawnSeconds = next(RESPAWN_SECONDS, this.respawnSeconds, 5); }
   public int lastStandSeconds() { return this.lastStandSeconds; }
   public void cycleLastStandSeconds() { this.lastStandSeconds = next(LAST_STAND_SECONDS, this.lastStandSeconds, 300); }
   public boolean friendlyFire() { return this.friendlyFire; }
   public void toggleFriendlyFire() { this.friendlyFire = !this.friendlyFire; }
   public boolean nightVision() { return this.nightVision; }
   public void toggleNightVision() { this.nightVision = !this.nightVision; }
   public boolean speed() { return this.speed; }
   public void toggleSpeed() { this.speed = !this.speed; }
   public boolean haste() { return this.haste; }
   public void toggleHaste() { this.haste = !this.haste; }
   public boolean teamArmor() { return this.teamArmor; }
   public void toggleTeamArmor() { this.teamArmor = !this.teamArmor; }
   public String onOff(boolean value) { return value ? "开" : "关"; }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("arenaLength", this.arenaLength);
      data.put("countdownSeconds", this.countdownSeconds);
      data.put("respawnSeconds", this.respawnSeconds);
      data.put("lastStandSeconds", this.lastStandSeconds);
      data.put("friendlyFire", this.friendlyFire);
      data.put("nightVision", this.nightVision);
      data.put("speed", this.speed);
      data.put("haste", this.haste);
      data.put("teamArmor", this.teamArmor);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) return;
      this.arenaLength = clamp(ARENA_LENGTHS, SettingsIo.asInt(data, "arenaLength", this.arenaLength), 72);
      this.countdownSeconds = clamp(COUNTDOWN_SECONDS, SettingsIo.asInt(data, "countdownSeconds", this.countdownSeconds), 5);
      this.respawnSeconds = clamp(RESPAWN_SECONDS, SettingsIo.asInt(data, "respawnSeconds", this.respawnSeconds), 5);
      this.lastStandSeconds = clamp(LAST_STAND_SECONDS, SettingsIo.asInt(data, "lastStandSeconds", this.lastStandSeconds), 300);
      this.friendlyFire = SettingsIo.asBool(data, "friendlyFire", this.friendlyFire);
      this.nightVision = SettingsIo.asBool(data, "nightVision", this.nightVision);
      this.speed = SettingsIo.asBool(data, "speed", this.speed);
      this.haste = SettingsIo.asBool(data, "haste", this.haste);
      this.teamArmor = SettingsIo.asBool(data, "teamArmor", this.teamArmor);
   }

   private static int next(int[] values, int current, int fallback) {
      for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
      return fallback;
   }

   private static int clamp(int[] values, int current, int fallback) {
      for (int value : values) if (value == current) return current;
      return fallback;
   }
}
