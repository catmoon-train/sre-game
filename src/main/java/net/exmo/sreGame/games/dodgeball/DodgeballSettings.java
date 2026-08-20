package net.exmo.sreGame.games.dodgeball;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class DodgeballSettings {
   private static final int[] ROUND_SECONDS = {60, 120, 180, 240};
   private static final int[] WINS = {1, 2, 3};

   private int roundSeconds = 180;
   private int winsNeeded = 2;
   private boolean powerups = true;
   private boolean frenzy = true;
   private boolean catchUp = true;

   public int roundSeconds() {
      return this.roundSeconds;
   }

   public int roundTicks() {
      return this.roundSeconds * 20;
   }

   public void cycleRoundSeconds() {
      this.roundSeconds = next(ROUND_SECONDS, this.roundSeconds, 180);
   }

   public int winsNeeded() {
      return this.winsNeeded;
   }

   public int totalRounds() {
      return this.winsNeeded * 2 - 1;
   }

   public void cycleWinsNeeded() {
      this.winsNeeded = next(WINS, this.winsNeeded, 2);
   }

   public boolean powerups() {
      return this.powerups;
   }

   public void togglePowerups() {
      this.powerups = !this.powerups;
   }

   public boolean frenzy() {
      return this.frenzy;
   }

   public void toggleFrenzy() {
      this.frenzy = !this.frenzy;
   }

   public boolean catchUp() {
      return this.catchUp;
   }

   public void toggleCatchUp() {
      this.catchUp = !this.catchUp;
   }

   public String onOff(boolean value) {
      return value ? "开" : "关";
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("roundSeconds", this.roundSeconds);
      data.put("winsNeeded", this.winsNeeded);
      data.put("powerups", this.powerups);
      data.put("frenzy", this.frenzy);
      data.put("catchUp", this.catchUp);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.roundSeconds = clampCycle(ROUND_SECONDS, SettingsIo.asInt(data, "roundSeconds", this.roundSeconds), 180);
      this.winsNeeded = clampCycle(WINS, SettingsIo.asInt(data, "winsNeeded", this.winsNeeded), 2);
      this.powerups = SettingsIo.asBool(data, "powerups", this.powerups);
      this.frenzy = SettingsIo.asBool(data, "frenzy", this.frenzy);
      this.catchUp = SettingsIo.asBool(data, "catchUp", this.catchUp);
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
