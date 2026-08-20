package net.exmo.sreGame.games.fakehuman;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class FakeHumanSettings {
   private static final int[] DAYS = {5, 6, 7};
   private static final int[] DAY_SECONDS = {180, 240, 300};

   private int days = 7;
   private int daySeconds = 240;

   public int days() {
      return this.days;
   }

   public int daySeconds() {
      return this.daySeconds;
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("days", this.days);
      data.put("daySeconds", this.daySeconds);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.days = SettingsIo.asInt(data, "days", this.days);
      this.daySeconds = SettingsIo.asInt(data, "daySeconds", this.daySeconds);
   }

   public void cycleDays() {
      this.days = next(DAYS, this.days, 7);
   }

   public void cycleDaySeconds() {
      this.daySeconds = next(DAY_SECONDS, this.daySeconds, 240);
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
