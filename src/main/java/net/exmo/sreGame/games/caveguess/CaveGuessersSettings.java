package net.exmo.sreGame.games.caveguess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class CaveGuessersSettings {
   private final int[] rounds = new int[CaveMode.values().length];
   private CaveDifficulty difficulty = CaveDifficulty.ALL;
   private boolean freeTuneGuess;

   public CaveGuessersSettings() {
      for (CaveMode mode : CaveMode.values()) {
         this.rounds[mode.ordinal()] = CaveMode.defaultRounds(mode);
      }
   }

   public int rounds(CaveMode mode) {
      return this.rounds[mode.ordinal()];
   }

   public void cycleRounds(CaveMode mode) {
      int i = mode.ordinal();
      this.rounds[i] = (this.rounds[i] + 1) % 6;
   }

   public CaveDifficulty difficulty() {
      return this.difficulty;
   }

   public void cycleDifficulty() {
      this.difficulty = this.difficulty.next();
   }

   public boolean freeTuneGuess() {
      return this.freeTuneGuess;
   }

   public void cycleFreeTuneGuess() {
      this.freeTuneGuess = !this.freeTuneGuess;
   }

   public String freeTuneLabel() {
      return this.freeTuneGuess ? "自由打字" : "四选一";
   }

   public List<CaveMode> schedule() {
      List<CaveMode> out = new ArrayList<>();
      for (CaveMode mode : CaveMode.values()) {
         int n = this.rounds[mode.ordinal()];
         for (int i = 0; i < n; i++) {
            out.add(mode);
         }
      }
      return out;
   }

   public int totalRounds() {
      return this.schedule().size();
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("difficulty", this.difficulty.name());
      data.put("freeTuneGuess", this.freeTuneGuess);
      Map<String, Object> modeRounds = new LinkedHashMap<>();
      for (CaveMode mode : CaveMode.values()) {
         modeRounds.put(mode.name(), this.rounds[mode.ordinal()]);
      }
      data.put("rounds", modeRounds);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      try {
         this.difficulty = CaveDifficulty.valueOf(SettingsIo.asString(data, "difficulty", this.difficulty.name()));
      } catch (IllegalArgumentException ignored) {
      }
      this.freeTuneGuess = SettingsIo.asBool(data, "freeTuneGuess", this.freeTuneGuess);
      Map<String, Object> modeRounds = SettingsIo.asMap(data, "rounds");
      for (CaveMode mode : CaveMode.values()) {
         this.rounds[mode.ordinal()] = Math.max(0, Math.min(5,
            SettingsIo.asInt(modeRounds, mode.name(), this.rounds[mode.ordinal()])));
      }
   }

   public String scheduleSummary() {
      StringBuilder sb = new StringBuilder();
      for (CaveMode mode : CaveMode.values()) {
         int n = this.rounds[mode.ordinal()];
         if (n <= 0) {
            continue;
         }
         if (!sb.isEmpty()) {
            sb.append(" · ");
         }
         sb.append(mode.display()).append("×").append(n);
      }
      return sb.isEmpty() ? "未选择模式" : sb.toString();
   }
}
