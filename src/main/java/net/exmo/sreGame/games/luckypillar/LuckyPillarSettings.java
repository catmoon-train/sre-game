package net.exmo.sreGame.games.luckypillar;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class LuckyPillarSettings {
   private static final int[] TEAM_SIZES = {2, 3, 4};
   private static final int[] REFRESH_SECONDS = {3, 5, 8, 10, 16};
   private static final int[] REFRESH_COUNTS = {1, 2, 3, 5};
   private static final int[] LURE_LEVELS = {1, 2, 3, 4, 5};
   private static final int[] BORDER_SIZES = {64, 96, 128, 160, 192};
   private static final int[] SHRINK_DELAYS = {60, 90, 120, 180};
   private static final int[] SHRINK_SPEED_TENTHS = {5, 10, 20};
   private static final int[] PILLAR_HEIGHTS = {32, 48, 64, 80};

   private boolean teams;
   private int teamSize = 2;
   private int refreshSeconds = 16;
   private int refreshCount = 1;
   private boolean luckyBlockMode;
   private FloorBlock floor = FloorBlock.WHITE_WOOL;
   private PillarBlock pillar = PillarBlock.OBSIDIAN;
   private boolean randomEvents = true;
   private boolean fishingMode;
   private int lureLevel = 1;
   private boolean border = true;
   private int borderSize = 128;
   private int shrinkDelaySeconds = 120;
   private int shrinkSpeedTenths = 5;
   private int pillarHeight = 64;

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

   public int refreshSeconds() {
      return this.refreshSeconds;
   }

   public void cycleRefreshSeconds() {
      this.refreshSeconds = next(REFRESH_SECONDS, this.refreshSeconds, 16);
   }

   public int refreshCount() {
      return this.refreshCount;
   }

   public void cycleRefreshCount() {
      this.refreshCount = next(REFRESH_COUNTS, this.refreshCount, 1);
   }

   public boolean luckyBlockMode() {
      return this.luckyBlockMode;
   }

   public void toggleLuckyBlockMode() {
      this.luckyBlockMode = !this.luckyBlockMode;
   }

   public FloorBlock floor() {
      return this.floor;
   }

   public void cycleFloor() {
      this.floor = this.floor.next();
   }

   public PillarBlock pillar() {
      return this.pillar;
   }

   public void cyclePillar() {
      this.pillar = this.pillar.next();
   }

   public boolean randomEvents() {
      return this.randomEvents;
   }

   public void toggleRandomEvents() {
      this.randomEvents = !this.randomEvents;
   }

   public boolean fishingMode() {
      return this.fishingMode;
   }

   public void toggleFishingMode() {
      this.fishingMode = !this.fishingMode;
   }

   public int lureLevel() {
      return this.lureLevel;
   }

   public void cycleLureLevel() {
      this.lureLevel = next(LURE_LEVELS, this.lureLevel, 1);
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
      this.borderSize = next(BORDER_SIZES, this.borderSize, 128);
   }

   public int shrinkDelaySeconds() {
      return this.shrinkDelaySeconds;
   }

   public void cycleShrinkDelay() {
      this.shrinkDelaySeconds = next(SHRINK_DELAYS, this.shrinkDelaySeconds, 120);
   }

   public int shrinkSpeedTenths() {
      return this.shrinkSpeedTenths;
   }

   public String shrinkSpeedLabel() {
      int ticks = this.ticksPerShrinkBlock();
      if (ticks % 20 == 0) {
         return (ticks / 20) + "s/格";
      }
      return (ticks / 20.0) + "s/格";
   }

   public void cycleShrinkSpeed() {
      this.shrinkSpeedTenths = next(SHRINK_SPEED_TENTHS, this.shrinkSpeedTenths, 5);
   }

   public int ticksPerShrinkBlock() {
      return Math.max(1, 200 / Math.max(1, this.shrinkSpeedTenths));
   }

   public int pillarHeight() {
      return this.pillarHeight;
   }

   public void cyclePillarHeight() {
      this.pillarHeight = next(PILLAR_HEIGHTS, this.pillarHeight, 64);
   }

   public String onOff(boolean value) {
      return value ? "开" : "关";
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("teams", this.teams);
      data.put("teamSize", this.teamSize);
      data.put("refreshSeconds", this.refreshSeconds);
      data.put("refreshCount", this.refreshCount);
      data.put("luckyBlockMode", this.luckyBlockMode);
      data.put("floor", this.floor.name());
      data.put("pillar", this.pillar.name());
      data.put("randomEvents", this.randomEvents);
      data.put("fishingMode", this.fishingMode);
      data.put("lureLevel", this.lureLevel);
      data.put("border", this.border);
      data.put("borderSize", this.borderSize);
      data.put("shrinkDelaySeconds", this.shrinkDelaySeconds);
      data.put("shrinkSpeedTenths", this.shrinkSpeedTenths);
      data.put("pillarHeight", this.pillarHeight);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.teams = SettingsIo.asBool(data, "teams", this.teams);
      this.teamSize = clampCycle(TEAM_SIZES, SettingsIo.asInt(data, "teamSize", this.teamSize), 2);
      this.refreshSeconds = clampCycle(REFRESH_SECONDS, SettingsIo.asInt(data, "refreshSeconds", this.refreshSeconds), 16);
      this.refreshCount = clampCycle(REFRESH_COUNTS, SettingsIo.asInt(data, "refreshCount", this.refreshCount), 1);
      this.luckyBlockMode = SettingsIo.asBool(data, "luckyBlockMode", this.luckyBlockMode);
      this.floor = FloorBlock.fromName(SettingsIo.asString(data, "floor", this.floor.name()));
      this.pillar = PillarBlock.fromName(SettingsIo.asString(data, "pillar", this.pillar.name()));
      this.randomEvents = SettingsIo.asBool(data, "randomEvents", this.randomEvents);
      this.fishingMode = SettingsIo.asBool(data, "fishingMode", this.fishingMode);
      this.lureLevel = clampCycle(LURE_LEVELS, SettingsIo.asInt(data, "lureLevel", this.lureLevel), 1);
      this.border = SettingsIo.asBool(data, "border", this.border);
      this.borderSize = clampCycle(BORDER_SIZES, SettingsIo.asInt(data, "borderSize", this.borderSize), 128);
      this.shrinkDelaySeconds = clampCycle(SHRINK_DELAYS, SettingsIo.asInt(data, "shrinkDelaySeconds", this.shrinkDelaySeconds), 120);
      this.shrinkSpeedTenths = clampCycle(SHRINK_SPEED_TENTHS, SettingsIo.asInt(data, "shrinkSpeedTenths", this.shrinkSpeedTenths), 5);
      this.pillarHeight = clampCycle(PILLAR_HEIGHTS, SettingsIo.asInt(data, "pillarHeight", this.pillarHeight), 64);
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
