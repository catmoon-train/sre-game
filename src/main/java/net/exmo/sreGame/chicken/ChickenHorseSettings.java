package net.exmo.sreGame.chicken;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class ChickenHorseSettings {
   private static final int[] ROUNDS = {3, 5, 7};
   private static final int[] PLACE_SECONDS = {30, 45, 60};
   private static final int[] RACE_SECONDS = {60, 75, 90};
   private static final int[] LENGTHS = {120, 168, 216};
   private static final int[] WIDTHS = {2, 3, 5};

   private int rounds = 5;
   private int placeSeconds = 45;
   private int raceSeconds = 75;
   private boolean goldEgg = true;
   private int length = 168;
   private int laneWidth = 3;

   public int rounds() {
      return this.rounds;
   }

   public int placeSeconds() {
      return this.placeSeconds;
   }

   public int raceSeconds() {
      return this.raceSeconds;
   }

   public boolean goldEgg() {
      return this.goldEgg;
   }

   public int length() {
      return this.length;
   }

   public int laneWidth() {
      return this.laneWidth;
   }

   public String goldEggLabel() {
      return this.goldEgg ? "开" : "关";
   }

   public String lengthLabel() {
      if (this.length <= 120) {
         return "短 " + this.length;
      }
      if (this.length >= 216) {
         return "长 " + this.length;
      }
      return "中 " + this.length;
   }

   public TrackLayout layout() {
      return TrackLayout.of(this.length, this.laneWidth);
   }

   public void cycleRounds() {
      this.rounds = next(ROUNDS, this.rounds, 5);
   }

   public void cyclePlaceSeconds() {
      this.placeSeconds = next(PLACE_SECONDS, this.placeSeconds, 45);
   }

   public void cycleRaceSeconds() {
      this.raceSeconds = next(RACE_SECONDS, this.raceSeconds, 75);
   }

   public void toggleGoldEgg() {
      this.goldEgg = !this.goldEgg;
   }

   public void cycleLength() {
      this.length = next(LENGTHS, this.length, 168);
   }

   public void cycleLaneWidth() {
      this.laneWidth = next(WIDTHS, this.laneWidth, 3);
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("rounds", this.rounds);
      data.put("placeSeconds", this.placeSeconds);
      data.put("raceSeconds", this.raceSeconds);
      data.put("goldEgg", this.goldEgg);
      data.put("length", this.length);
      data.put("laneWidth", this.laneWidth);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.rounds = SettingsIo.asInt(data, "rounds", this.rounds);
      this.placeSeconds = SettingsIo.asInt(data, "placeSeconds", this.placeSeconds);
      this.raceSeconds = SettingsIo.asInt(data, "raceSeconds", this.raceSeconds);
      this.goldEgg = SettingsIo.asBool(data, "goldEgg", this.goldEgg);
      this.length = snap(LENGTHS, SettingsIo.asInt(data, "length", this.length), 168);
      this.laneWidth = snap(WIDTHS, SettingsIo.asInt(data, "laneWidth", this.laneWidth), 3);
   }

   public int trapOfferCount() {
      return 6;
   }

   public int trapPickMin() {
      return 1;
   }

   public int trapPickMax() {
      return 2;
   }

   private static int snap(int[] cycle, int current, int fallback) {
      for (int value : cycle) {
         if (value == current) {
            return current;
         }
      }
      return fallback;
   }

   private static int next(int[] cycle, int current, int fallback) {
      for (int i = 0; i < cycle.length; i++) {
         if (cycle[i] == current) {
            return cycle[(i + 1) % cycle.length];
         }
      }
      return fallback;
   }
}
