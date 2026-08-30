package net.exmo.sreGame.games.partygames;

import java.util.LinkedHashMap;
import java.util.Map;
import net.exmo.sreGame.profile.SettingsIo;

/** Per-room settings shared by the party-game catalogue. */
public final class PartyGameSettings {
   private static final int[] DURATIONS = {60, 90, 120, 180};
   private final Map<String, String> lockedMaps = new LinkedHashMap<>();
   private final Map<String, Integer> durations = new LinkedHashMap<>();

   public String mapId(PartyGameType type) {
      return type == null ? "" : this.lockedMaps.getOrDefault(type.id(), "");
   }

   public void setMapId(PartyGameType type, String id) {
      if (type == null) return;
      if (id == null || id.isBlank()) this.lockedMaps.remove(type.id());
      else this.lockedMaps.put(type.id(), id);
   }

   public int durationSeconds(PartyGameType type) {
      return this.durations.getOrDefault(type.id(), 90);
   }

   public void cycleDuration(PartyGameType type) {
      int current = durationSeconds(type);
      for (int i = 0; i < DURATIONS.length; i++) {
         if (DURATIONS[i] == current) {
            this.durations.put(type.id(), DURATIONS[(i + 1) % DURATIONS.length]);
            return;
         }
      }
      this.durations.put(type.id(), 90);
   }

   public Map<String, Object> snapshot() {
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("lockedMaps", new LinkedHashMap<>(this.lockedMaps));
      data.put("durations", new LinkedHashMap<>(this.durations));
      return data;
   }

   public void apply(Map<String, Object> data) {
      this.lockedMaps.clear();
      this.durations.clear();
      for (Map.Entry<String, Object> entry : SettingsIo.asMap(data, "lockedMaps").entrySet()) {
         String id = String.valueOf(entry.getValue());
         if (!id.isBlank() && PartyGameType.byId(entry.getKey()) != null) this.lockedMaps.put(entry.getKey(), id);
      }
      for (Map.Entry<String, Object> entry : SettingsIo.asMap(data, "durations").entrySet()) {
         PartyGameType type = PartyGameType.byId(entry.getKey());
         int seconds = SettingsIo.asInt(Map.of("value", entry.getValue()), "value", 90);
         if (type != null && (seconds == 60 || seconds == 90 || seconds == 120 || seconds == 180)) {
            this.durations.put(type.id(), seconds);
         }
      }
   }
}
