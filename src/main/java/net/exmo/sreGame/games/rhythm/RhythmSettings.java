package net.exmo.sreGame.games.rhythm;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class RhythmSettings {

   public enum Mode {
      SOLO("单人"),
      COOP("合作"),
      VERSUS("对战"),
      PURE_LEFT("纯左键");

      private final String label;

      Mode(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      public int minPlayers() {
         return 1;
      }

      public int maxPlayers() {
         return switch (this) {
            case SOLO, PURE_LEFT -> 1;
            case COOP -> 2;
            case VERSUS -> 4;
         };
      }
   }

   public enum Strictness {
      NOVICE("新手"),
      NORMAL("普通"),
      HARD("困难"),
      EXPERT("专家");

      private final String label;

      Strictness(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }
   }

   public enum Orientation {
      VERTICAL("纵向"),
      HORIZONTAL("横向"),
      FRONTAL("由远及近");

      private final String label;

      Orientation(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }
   }

   private Mode mode = Mode.SOLO;
   private int speed = 1;                    // 1..3 下落速度倍率
   private Strictness strictness = Strictness.NOVICE;
   private Orientation orientation = Orientation.VERTICAL;
   private String chart = "beginner";        // 谱面 id 或 "random"（默认最简曲目）

   public Mode mode() {
      return this.mode;
   }

   public void setMode(Mode mode) {
      this.mode = mode == null ? Mode.SOLO : mode;
   }

   public void cycleMode() {
      this.mode = switch (this.mode) {
         case SOLO -> Mode.COOP;
         case COOP -> Mode.VERSUS;
         case VERSUS -> Mode.PURE_LEFT;
         case PURE_LEFT -> Mode.SOLO;
      };
   }

   public int speed() {
      return this.speed;
   }

   public double speedMultiplier() {
      return this.speed;
   }

   public void cycleSpeed() {
      this.speed = this.speed >= 3 ? 1 : this.speed + 1;
   }

   public Strictness strictness() {
      return this.strictness;
   }

   /** Perfect 判定窗口（毫秒）。四档：新手 320 / 普通 220 / 困难 150 / 专家 100。 */
   public int perfectWindowMs() {
      return switch (this.strictness) {
         case NOVICE -> 320;
         case NORMAL -> 220;
         case HARD -> 150;
         case EXPERT -> 100;
      };
   }

   /** Great 判定窗口（毫秒）。四档：新手 700 / 普通 450 / 困难 300 / 专家 200。 */
   public int greatWindowMs() {
      return switch (this.strictness) {
         case NOVICE -> 700;
         case NORMAL -> 450;
         case HARD -> 300;
         case EXPERT -> 200;
      };
   }

   public void cycleStrictness() {
      this.strictness = switch (this.strictness) {
         case NOVICE -> Strictness.NORMAL;
         case NORMAL -> Strictness.HARD;
         case HARD -> Strictness.EXPERT;
         case EXPERT -> Strictness.NOVICE;
      };
   }

   public Orientation orientation() {
      return this.orientation;
   }

   public void cycleOrientation() {
      this.orientation = switch (this.orientation) {
         case VERTICAL -> Orientation.HORIZONTAL;
         case HORIZONTAL -> Orientation.FRONTAL;
         case FRONTAL -> Orientation.VERTICAL;
      };
   }

   public String chart() {
      return this.chart;
   }

   public void setChart(String chart) {
      this.chart = chart == null || chart.isBlank() ? "random" : chart;
   }

   public String chartLabel(ChartLibrary library) {
      if ("random".equals(this.chart)) {
         return "随机";
      }
      RhythmChart c = library.get(this.chart);
      return c != null ? c.name : "随机";
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("mode", this.mode.name());
      data.put("speed", this.speed);
      data.put("strictness", this.strictness.name());
      data.put("orientation", this.orientation.name());
      data.put("chart", this.chart);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      try {
         this.mode = Mode.valueOf(SettingsIo.asString(data, "mode", this.mode.name()));
      } catch (IllegalArgumentException ignored) {
      }
      this.speed = Math.max(1, Math.min(3, SettingsIo.asInt(data, "speed", this.speed)));
      try {
         this.strictness = Strictness.valueOf(SettingsIo.asString(data, "strictness", this.strictness.name()));
      } catch (IllegalArgumentException ignored) {
      }
      try {
         this.orientation = Orientation.valueOf(SettingsIo.asString(data, "orientation", this.orientation.name()));
      } catch (IllegalArgumentException ignored) {
      }
      this.setChart(SettingsIo.asString(data, "chart", this.chart));
   }
}
