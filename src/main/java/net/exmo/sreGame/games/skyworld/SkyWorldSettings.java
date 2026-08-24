package net.exmo.sreGame.games.skyworld;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class SkyWorldSettings {
   private static final int[] TEAM_SIZES = {2, 3, 4};
   private static final int[] GRACE_SECONDS = {0, 5, 10, 15};
   private static final int[] REFILL_SECONDS = {60, 120, 180, 300};
   private static final int[] BORDER_SIZES = {64, 96, 128};
   private static final int[] SHRINK_DELAYS = {60, 90, 120, 180};
   private static final int[] SHRINK_TICKS = {40, 20, 10, 80, 160};

   private boolean teams;
   private int teamSize = 2;
   private boolean friendlyFire;
   private ChestTier chestTier = ChestTier.NORMAL;
   private int pvpGraceSeconds = 10;
   private boolean refill = true;
   private int refillSeconds = 180;
   private boolean border;
   private int borderSize = 96;
   private int shrinkDelaySeconds = 120;
   private int shrinkTicksPerBlock = 40;

   public boolean teams() {
      return this.teams;
   }

   public void toggleTeams() {
      this.teams = !this.teams;
   }

   public int teamSize() {
      return this.teamSize;
   }

   public void cycleTeamSize() {
      this.teamSize = next(TEAM_SIZES, this.teamSize, 2);
   }

   public boolean friendlyFire() {
      return this.friendlyFire;
   }

   public void toggleFriendlyFire() {
      this.friendlyFire = !this.friendlyFire;
   }

   public ChestTier chestTier() {
      return this.chestTier;
   }

   public void cycleChestTier() {
      this.chestTier = this.chestTier.next();
   }

   public int pvpGraceSeconds() {
      return this.pvpGraceSeconds;
   }

   public void cyclePvpGrace() {
      this.pvpGraceSeconds = next(GRACE_SECONDS, this.pvpGraceSeconds, 10);
   }

   public boolean refill() {
      return this.refill;
   }

   public void toggleRefill() {
      this.refill = !this.refill;
   }

   public int refillSeconds() {
      return this.refillSeconds;
   }

   public void cycleRefillSeconds() {
      this.refillSeconds = next(REFILL_SECONDS, this.refillSeconds, 180);
   }

   public boolean border() {
      return this.border;
   }

   public void toggleBorder() {
      this.border = !this.border;
   }

   public int borderSize() {
      return this.borderSize;
   }

   public void cycleBorderSize() {
      this.borderSize = next(BORDER_SIZES, this.borderSize, 96);
   }

   public int shrinkDelaySeconds() {
      return this.shrinkDelaySeconds;
   }

   public void cycleShrinkDelay() {
      this.shrinkDelaySeconds = next(SHRINK_DELAYS, this.shrinkDelaySeconds, 120);
   }

   public int ticksPerShrinkBlock() {
      return Math.max(1, this.shrinkTicksPerBlock);
   }

   public String shrinkSpeedLabel() {
      int ticks = this.ticksPerShrinkBlock();
      if (ticks % 20 == 0) {
         return (ticks / 20) + "s/格";
      }
      return (ticks / 20.0) + "s/格";
   }

   public void cycleShrinkSpeed() {
      this.shrinkTicksPerBlock = next(SHRINK_TICKS, this.shrinkTicksPerBlock, 40);
   }

   public String onOff(boolean value) {
      return value ? "开" : "关";
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("teams", this.teams);
      data.put("teamSize", this.teamSize);
      data.put("friendlyFire", this.friendlyFire);
      data.put("chestTier", this.chestTier.name());
      data.put("pvpGraceSeconds", this.pvpGraceSeconds);
      data.put("refill", this.refill);
      data.put("refillSeconds", this.refillSeconds);
      data.put("border", this.border);
      data.put("borderSize", this.borderSize);
      data.put("shrinkDelaySeconds", this.shrinkDelaySeconds);
      data.put("shrinkTicksPerBlock", this.shrinkTicksPerBlock);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.teams = SettingsIo.asBool(data, "teams", this.teams);
      this.teamSize = clampCycle(TEAM_SIZES, SettingsIo.asInt(data, "teamSize", this.teamSize), 2);
      this.friendlyFire = SettingsIo.asBool(data, "friendlyFire", this.friendlyFire);
      this.chestTier = ChestTier.fromName(SettingsIo.asString(data, "chestTier", this.chestTier.name()));
      this.pvpGraceSeconds = clampCycle(GRACE_SECONDS, SettingsIo.asInt(data, "pvpGraceSeconds", this.pvpGraceSeconds), 10);
      this.refill = SettingsIo.asBool(data, "refill", this.refill);
      this.refillSeconds = clampCycle(REFILL_SECONDS, SettingsIo.asInt(data, "refillSeconds", this.refillSeconds), 180);
      this.border = SettingsIo.asBool(data, "border", this.border);
      this.borderSize = clampCycle(BORDER_SIZES, SettingsIo.asInt(data, "borderSize", this.borderSize), 96);
      this.shrinkDelaySeconds = clampCycle(SHRINK_DELAYS, SettingsIo.asInt(data, "shrinkDelaySeconds", this.shrinkDelaySeconds), 120);
      this.shrinkTicksPerBlock = clampCycle(SHRINK_TICKS, SettingsIo.asInt(data, "shrinkTicksPerBlock", this.shrinkTicksPerBlock), 40);
   }

   private static int next(int[] cycle, int current, int fallback) {
      for (int i = 0; i < cycle.length; i++) {
         if (cycle[i] == current) {
            return cycle[(i + 1) % cycle.length];
         }
      }
      return fallback;
   }

   private static int clampCycle(int[] cycle, int current, int fallback) {
      for (int value : cycle) {
         if (value == current) {
            return current;
         }
      }
      return fallback;
   }
}
