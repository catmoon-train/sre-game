package net.exmo.sreGame.games.dig;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

public final class DigToDeathSettings {
   private static final int[] LAYERS = {2, 3, 4, 5, 6};

   private DigVariant variant = DigVariant.SHOVEL;
   private int layers = 3;

   public DigVariant variant() {
      return this.variant;
   }

   public void cycleVariant() {
      this.variant = this.variant.next();
   }

   public int layers() {
      return this.layers;
   }

   public void cycleLayers() {
      this.layers = next(LAYERS, this.layers, 3);
   }

   public String onOff(boolean value) {
      return value ? "开" : "关";
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("variant", this.variant.name());
      data.put("layers", this.layers);
      return data;
   }

   public void apply(Map<String, Object> data) {
      if (data == null || data.isEmpty()) {
         return;
      }
      this.variant = DigVariant.fromName(SettingsIo.asString(data, "variant", this.variant.name()));
      this.layers = clampCycle(LAYERS, SettingsIo.asInt(data, "layers", this.layers), 3);
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
