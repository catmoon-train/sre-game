package net.exmo.sreGame.games.pushthebutton;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class PushTheButtonSettings {
   private int alienCount;
   private int jesterChance = 15;
   private boolean drawing = true;
   private boolean bio = true;

   public int alienCount() {
      return this.alienCount;
   }

   public void cycleAlienCount() {
      this.alienCount = this.alienCount >= 3 ? 0 : this.alienCount + 1;
   }

   public String alienCountLabel() {
      return this.alienCount <= 0 ? "自动" : String.valueOf(this.alienCount);
   }

   public int resolvedAliens(int players) {
      int max = players <= 5 ? 1 : players <= 8 ? 2 : 3;
      int want = this.alienCount <= 0
         ? (players <= 5 ? 1 : players <= 8 ? 2 : 3)
         : this.alienCount;
      return Math.max(1, Math.min(max, Math.min(want, players - 1)));
   }

   public int jesterChance() {
      return this.jesterChance;
   }

   public void cycleJesterChance() {
      if (this.jesterChance >= 100) {
         this.jesterChance = 0;
      } else {
         this.jesterChance = Math.min(100, this.jesterChance + 5);
      }
   }

   public String jesterChanceLabel() {
      if (this.jesterChance <= 0) {
         return "关";
      }
      if (this.jesterChance >= 100) {
         return "必出";
      }
      return this.jesterChance + "%";
   }

   public boolean drawing() {
      return this.drawing;
   }

   public void cycleDrawing() {
      this.drawing = !this.drawing;
   }

   public boolean bio() {
      return this.bio;
   }

   public void cycleBio() {
      this.bio = !this.bio;
   }

   public String onOff(boolean value) {
      return value ? "开" : "关";
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("alienCount", this.alienCount);
      data.put("jesterChance", this.jesterChance);
      data.put("drawing", this.drawing);
      data.put("bio", this.bio);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.alienCount = Math.max(0, Math.min(3, SettingsIo.asInt(data, "alienCount", this.alienCount)));
      this.jesterChance = Math.max(0, Math.min(100, SettingsIo.asInt(data, "jesterChance", this.jesterChance)));
      this.drawing = SettingsIo.asBool(data, "drawing", this.drawing);
      this.bio = SettingsIo.asBool(data, "bio", this.bio);
   }
}
