package net.exmo.sreGame.games.nametagwar;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class NameTagWarSettings {
   public enum RipMode {
      FAST("&b速撕 &7(0.25s · 背后即可)", 5, 0.0),
      STEADY("&e稳撕 &7(0.5s · 侧前即可)", 10, 0.3);

      private final String label;
      private final int ticks;
      private final double angleThreshold;

      RipMode(String label, int ticks, double angleThreshold) {
         this.label = label;
         this.ticks = ticks;
         this.angleThreshold = angleThreshold;
      }

      public String label() {
         return this.label;
      }

      public int ticks() {
         return this.ticks;
      }

      public double angleThreshold() {
         return this.angleThreshold;
      }

      public RipMode next() {
         return values()[(this.ordinal() + 1) % values().length];
      }

      public static RipMode fromName(String name) {
         if (name == null) {
            return FAST;
         }
         for (RipMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
               return mode;
            }
         }
         return FAST;
      }
   }

   private static final int[] TEAM_SIZES = {2, 3, 4};
   private static final int[] BORDER_SIZES = {48, 64, 96, 128};
   private static final int[] SHRINK_DELAYS = {60, 90, 120, 180};
   private static final int[] SHRINK_TICKS = {40, 20, 10, 80, 160};
   private static final int[] MAX_SECONDS = {120, 180, 240, 300, 600};

   private boolean teams;
   private int teamSize = 2;
   private boolean friendlyFire;
   private RipMode defaultRipMode = RipMode.FAST;
   private boolean giveBothRippers = true;
   private int maxSeconds = 180;
   private boolean border = true;
   private int borderSize = 64;
   private int shrinkDelaySeconds = 120;
   private int shrinkTicksPerBlock = 40;
   private boolean interruptOnMove = true;
   private boolean interruptOnDamage = true;
   private double maxDistance = 3.0;
   private double horizontalOffset = 0.5;
   private double heightOffset = 1.2;
   private double sneakHeightReduce = 0.2;

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

   public RipMode defaultRipMode() {
      return this.defaultRipMode;
   }

   public void cycleDefaultRipMode() {
      this.defaultRipMode = this.defaultRipMode.next();
   }

   public boolean giveBothRippers() {
      return this.giveBothRippers;
   }

   public void toggleGiveBothRippers() {
      this.giveBothRippers = !this.giveBothRippers;
   }

   public int maxSeconds() {
      return this.maxSeconds;
   }

   public void cycleMaxSeconds() {
      this.maxSeconds = next(MAX_SECONDS, this.maxSeconds, 240);
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
      this.borderSize = next(BORDER_SIZES, this.borderSize, 64);
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

   public boolean interruptOnMove() {
      return this.interruptOnMove;
   }

   public void toggleInterruptOnMove() {
      this.interruptOnMove = !this.interruptOnMove;
   }

   public boolean interruptOnDamage() {
      return this.interruptOnDamage;
   }

   public void toggleInterruptOnDamage() {
      this.interruptOnDamage = !this.interruptOnDamage;
   }

   public double maxDistance() {
      return this.maxDistance;
   }

   public double maxDistanceSq() {
      return this.maxDistance * this.maxDistance;
   }

   public double horizontalOffset() {
      return this.horizontalOffset;
   }

   public double heightOffset() {
      return this.heightOffset;
   }

   public double sneakHeightReduce() {
      return this.sneakHeightReduce;
   }

   public String onOff(boolean value) {
      return value ? "开" : "关";
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("teams", this.teams);
      data.put("teamSize", this.teamSize);
      data.put("friendlyFire", this.friendlyFire);
      data.put("defaultRipMode", this.defaultRipMode.name());
      data.put("giveBothRippers", this.giveBothRippers);
      data.put("maxSeconds", this.maxSeconds);
      data.put("border", this.border);
      data.put("borderSize", this.borderSize);
      data.put("shrinkDelaySeconds", this.shrinkDelaySeconds);
      data.put("shrinkTicksPerBlock", this.shrinkTicksPerBlock);
      data.put("interruptOnMove", this.interruptOnMove);
      data.put("interruptOnDamage", this.interruptOnDamage);
      data.put("maxDistance", this.maxDistance);
      data.put("horizontalOffset", this.horizontalOffset);
      data.put("heightOffset", this.heightOffset);
      data.put("sneakHeightReduce", this.sneakHeightReduce);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.teams = SettingsIo.asBool(data, "teams", this.teams);
      this.teamSize = clampCycle(TEAM_SIZES, SettingsIo.asInt(data, "teamSize", this.teamSize), 2);
      this.friendlyFire = SettingsIo.asBool(data, "friendlyFire", this.friendlyFire);
      this.defaultRipMode = RipMode.fromName(SettingsIo.asString(data, "defaultRipMode", this.defaultRipMode.name()));
      this.giveBothRippers = SettingsIo.asBool(data, "giveBothRippers", this.giveBothRippers);
      this.maxSeconds = clampCycle(MAX_SECONDS, SettingsIo.asInt(data, "maxSeconds", this.maxSeconds), 240);
      this.border = SettingsIo.asBool(data, "border", this.border);
      this.borderSize = clampCycle(BORDER_SIZES, SettingsIo.asInt(data, "borderSize", this.borderSize), 64);
      this.shrinkDelaySeconds = clampCycle(SHRINK_DELAYS, SettingsIo.asInt(data, "shrinkDelaySeconds", this.shrinkDelaySeconds), 120);
      this.shrinkTicksPerBlock = clampCycle(SHRINK_TICKS, SettingsIo.asInt(data, "shrinkTicksPerBlock", this.shrinkTicksPerBlock), 40);
      this.interruptOnMove = SettingsIo.asBool(data, "interruptOnMove", this.interruptOnMove);
      this.interruptOnDamage = SettingsIo.asBool(data, "interruptOnDamage", this.interruptOnDamage);
      this.maxDistance = SettingsIo.asDouble(data, "maxDistance", this.maxDistance);
      this.horizontalOffset = SettingsIo.asDouble(data, "horizontalOffset", this.horizontalOffset);
      this.heightOffset = SettingsIo.asDouble(data, "heightOffset", this.heightOffset);
      this.sneakHeightReduce = SettingsIo.asDouble(data, "sneakHeightReduce", this.sneakHeightReduce);
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
