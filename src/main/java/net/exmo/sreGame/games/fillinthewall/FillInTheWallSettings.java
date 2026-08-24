package net.exmo.sreGame.games.fillinthewall;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class FillInTheWallSettings {
   public enum Mode {
      ENDLESS("无尽难度"), TIMED("限时计分");

      private final String label;

      Mode(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      Mode next() {
         return values()[(this.ordinal() + 1) % values().length];
      }

      static Mode fromName(String name) {
         try {
            return valueOf(name);
         } catch (Exception e) {
            return ENDLESS;
         }
      }
   }

   private static final int[] LENGTHS = {5, 6, 7, 8, 9, 10, 12};
   private static final int[] HEIGHTS = {3, 4, 5, 6, 7, 8};
   private static final int[] DURATIONS = {60, 120, 180, 240, 300, 420, 600};
   private static final int[] WALL_TIMES = {100, 120, 140, 160, 180, 200, 240};
   private static final int[] RANDOM_HOLES = {1, 2, 3, 4};
   private static final int[] CONNECTED_HOLES = {0, 1, 2, 3, 4, 5, 6};
   private static final int[] STAND_DIST = {3, 4, 5, 6, 8, 10};
   private static final int[] PERFECT_CAPS = {0, 5, 10, 15, 20, 30, 50};

   private Mode mode = Mode.ENDLESS;
   private int length = 7;
   private int height = 4;
   private int durationSeconds = 180;
   private int wallActiveTime = 160;
   private int randomHoles = 2;
   private int connectedHoles = 4;
   private boolean randomizeFurther = true;
   private int standingDistance = 5;
   private int perfectWallCap = 0;
   private boolean highlightIncorrect = true;

   public Mode mode() {
      return this.mode;
   }

   public void cycleMode() {
      this.mode = this.mode.next();
   }

   public int length() {
      return this.length;
   }

   public void cycleLength() {
      this.length = next(LENGTHS, this.length, 7);
   }

   public int height() {
      return this.height;
   }

   public void cycleHeight() {
      this.height = next(HEIGHTS, this.height, 4);
   }

   public int durationSeconds() {
      return this.durationSeconds;
   }

   public void cycleDuration() {
      this.durationSeconds = next(DURATIONS, this.durationSeconds, 180);
   }

   public int wallActiveTime() {
      return this.wallActiveTime;
   }

   public void cycleWallTime() {
      this.wallActiveTime = next(WALL_TIMES, this.wallActiveTime, 160);
   }

   public int randomHoles() {
      return this.randomHoles;
   }

   public void cycleRandomHoles() {
      this.randomHoles = next(RANDOM_HOLES, this.randomHoles, 2);
   }

   public int connectedHoles() {
      return this.connectedHoles;
   }

   public void cycleConnectedHoles() {
      this.connectedHoles = next(CONNECTED_HOLES, this.connectedHoles, 4);
   }

   public boolean randomizeFurther() {
      return this.randomizeFurther;
   }

   public void toggleRandomizeFurther() {
      this.randomizeFurther = !this.randomizeFurther;
   }

   public int standingDistance() {
      return this.standingDistance;
   }

   public void cycleStandingDistance() {
      this.standingDistance = next(STAND_DIST, this.standingDistance, 5);
   }

   public int perfectWallCap() {
      return this.perfectWallCap;
   }

   public void cyclePerfectWallCap() {
      this.perfectWallCap = next(PERFECT_CAPS, this.perfectWallCap, 0);
   }

   public boolean highlightIncorrect() {
      return this.highlightIncorrect;
   }

   public void toggleHighlightIncorrect() {
      this.highlightIncorrect = !this.highlightIncorrect;
   }

   public String onOff(boolean value) {
      return value ? "开" : "关";
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("mode", this.mode.name());
      data.put("length", this.length);
      data.put("height", this.height);
      data.put("durationSeconds", this.durationSeconds);
      data.put("wallActiveTime", this.wallActiveTime);
      data.put("randomHoles", this.randomHoles);
      data.put("connectedHoles", this.connectedHoles);
      data.put("randomizeFurther", this.randomizeFurther);
      data.put("standingDistance", this.standingDistance);
      data.put("perfectWallCap", this.perfectWallCap);
      data.put("highlightIncorrect", this.highlightIncorrect);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.mode = Mode.fromName(SettingsIo.asString(data, "mode", this.mode.name()));
      this.length = clampCycle(LENGTHS, SettingsIo.asInt(data, "length", this.length), 7);
      this.height = clampCycle(HEIGHTS, SettingsIo.asInt(data, "height", this.height), 4);
      this.durationSeconds = clampCycle(DURATIONS, SettingsIo.asInt(data, "durationSeconds", this.durationSeconds), 180);
      this.wallActiveTime = clampCycle(WALL_TIMES, SettingsIo.asInt(data, "wallActiveTime", this.wallActiveTime), 160);
      this.randomHoles = clampCycle(RANDOM_HOLES, SettingsIo.asInt(data, "randomHoles", this.randomHoles), 2);
      this.connectedHoles = clampCycle(CONNECTED_HOLES, SettingsIo.asInt(data, "connectedHoles", this.connectedHoles), 4);
      this.randomizeFurther = SettingsIo.asBool(data, "randomizeFurther", this.randomizeFurther);
      this.standingDistance = clampCycle(STAND_DIST, SettingsIo.asInt(data, "standingDistance", this.standingDistance), 5);
      this.perfectWallCap = clampCycle(PERFECT_CAPS, SettingsIo.asInt(data, "perfectWallCap", this.perfectWallCap), 0);
      this.highlightIncorrect = SettingsIo.asBool(data, "highlightIncorrect", this.highlightIncorrect);
   }

   static int next(int[] cycle, int current, int fallback) {
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
