package net.exmo.sreGame.buildwar;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class BuildWarSettings {
   private static final int MAX_ROUNDS = 8;
   private static final int[] BUILD_CYCLE = {30, 60, 90, 120, 150};

   private int rounds = 3;
   private final int[] buildSeconds = {90, 90, 90, 90, 90, 90, 90, 90};
   private int guessSeconds = 45;
   private int themeCount = 0;
   /** 主题比人少时：false=其余人旁观（默认），true=多人同组一起建 */
   private boolean extraBuildTogether = false;
   /** 开局手动输入主题，超时随机。默认关。 */
   private boolean customTheme = false;
   /** 开局从 3 个不重复主题中挑选。默认开。自定主题开启时优先进聊天输入。 */
   private boolean pickFromThree = true;

   public int themeCount() {
      return this.themeCount;
   }

   public boolean extraBuildTogether() {
      return this.extraBuildTogether;
   }

   public void cycleExtraPlayers() {
      this.extraBuildTogether = !this.extraBuildTogether;
   }

   public String extraPlayersLabel() {
      return this.extraBuildTogether ? "一起建" : "旁观";
   }

   public boolean customTheme() {
      return this.customTheme;
   }

   public void cycleCustomTheme() {
      this.customTheme = !this.customTheme;
   }

   public String customThemeLabel() {
      return this.customTheme ? "开" : "关";
   }

   public boolean pickFromThree() {
      return this.pickFromThree;
   }

   public void cyclePickFromThree() {
      this.pickFromThree = !this.pickFromThree;
   }

   public String pickFromThreeLabel() {
      return this.pickFromThree ? "开" : "关";
   }

   public String themeCountLabel() {
      return this.themeCount <= 0 ? "自动(=人数)" : String.valueOf(this.themeCount);
   }

   public void cycleThemeCount() {
      if (this.themeCount <= 0) {
         this.themeCount = 1;
      } else if (this.themeCount >= 20) {
         this.themeCount = 0;
      } else {
         this.themeCount++;
      }
   }

   public int resolvedThemeCount(int players) {
      int n = this.themeCount <= 0 ? players : this.themeCount;
      return Math.max(1, Math.min(players, n));
   }

   public int rounds() {
      return this.rounds;
   }

   public void setRounds(int rounds) {
      this.rounds = Math.max(1, Math.min(MAX_ROUNDS, rounds));
   }

   public int buildSeconds() {
      return this.buildSeconds[0];
   }

   public int buildSecondsForRound(int round) {
      int index = Math.max(0, Math.min(MAX_ROUNDS - 1, round - 1));
      return this.buildSeconds[index];
   }

   public void cycleBuildSecondsForRound(int round) {
      int index = Math.max(0, Math.min(MAX_ROUNDS - 1, round - 1));
      this.buildSeconds[index] = next(BUILD_CYCLE, this.buildSeconds[index], 90);
   }

   public String buildTimesSummary() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < this.rounds; i++) {
         if (i > 0) {
            sb.append('/');
         }
         sb.append(this.buildSeconds[i]);
      }
      return sb.append('s').toString();
   }

   public int guessSeconds() {
      return this.guessSeconds;
   }

   public void cycleGuessSeconds() {
      int[] cycle = {20, 30, 45, 60, 90};
      this.guessSeconds = next(cycle, this.guessSeconds, 45);
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("rounds", this.rounds);
      data.put("buildSeconds", SettingsIo.intList(this.buildSeconds));
      data.put("guessSeconds", this.guessSeconds);
      data.put("themeCount", this.themeCount);
      data.put("extraBuildTogether", this.extraBuildTogether);
      data.put("customTheme", this.customTheme);
      data.put("pickFromThree", this.pickFromThree);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.setRounds(SettingsIo.asInt(data, "rounds", this.rounds));
      int[] loaded = SettingsIo.asIntArray(data, "buildSeconds", this.buildSeconds);
      System.arraycopy(loaded, 0, this.buildSeconds, 0, Math.min(this.buildSeconds.length, loaded.length));
      this.guessSeconds = SettingsIo.asInt(data, "guessSeconds", this.guessSeconds);
      this.themeCount = Math.max(0, Math.min(20, SettingsIo.asInt(data, "themeCount", this.themeCount)));
      this.extraBuildTogether = SettingsIo.asBool(data, "extraBuildTogether", this.extraBuildTogether);
      this.customTheme = SettingsIo.asBool(data, "customTheme", this.customTheme);
      this.pickFromThree = SettingsIo.asBool(data, "pickFromThree", this.pickFromThree);
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
