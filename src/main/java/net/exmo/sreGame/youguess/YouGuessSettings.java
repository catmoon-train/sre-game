package net.exmo.sreGame.youguess;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class YouGuessSettings {
   private static final int[] BUILD_CYCLE = {30, 60, 90, 120, 150};

   private int rounds = 0;
   private int buildSeconds = 90;
   /** 每轮建造者手动输入主题，超时随机。默认关。 */
   private boolean customTheme = false;
   /** 开局/每轮从 3 个不重复主题中挑选。默认开。 */
   private boolean pickFromThree = true;

   public int rounds() {
      return this.rounds;
   }

   public String roundsLabel(int players) {
      return this.rounds <= 0 ? "自动(=人数 " + Math.max(2, players) + ")" : String.valueOf(this.rounds);
   }

   public void cycleRounds() {
      if (this.rounds <= 0) {
         this.rounds = 1;
      } else if (this.rounds >= 20) {
         this.rounds = 0;
      } else {
         this.rounds++;
      }
   }

   public int resolvedRounds(int players) {
      int n = this.rounds <= 0 ? players : this.rounds;
      return Math.max(1, Math.min(20, n));
   }

   public int buildSeconds() {
      return this.buildSeconds;
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

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("rounds", this.rounds);
      data.put("buildSeconds", this.buildSeconds);
      data.put("customTheme", this.customTheme);
      data.put("pickFromThree", this.pickFromThree);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.rounds = Math.max(0, Math.min(20, SettingsIo.asInt(data, "rounds", this.rounds)));
      this.buildSeconds = SettingsIo.asInt(data, "buildSeconds", this.buildSeconds);
      this.customTheme = SettingsIo.asBool(data, "customTheme", this.customTheme);
      this.pickFromThree = SettingsIo.asBool(data, "pickFromThree", this.pickFromThree);
   }

   public void cycleBuildSeconds() {
      for (int i = 0; i < BUILD_CYCLE.length; i++) {
         if (BUILD_CYCLE[i] == this.buildSeconds) {
            this.buildSeconds = BUILD_CYCLE[(i + 1) % BUILD_CYCLE.length];
            return;
         }
      }
      this.buildSeconds = 90;
   }
}
