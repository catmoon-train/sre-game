package net.exmo.sreGame.games.partygames;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persisted, parameter-only procedural map template. */
public final class MapTemplate {
   private int version = 1;
   private String id = "";
   private String gameId = "";
   private boolean enabled = true;
   private boolean defaultTemplate;
   private long seed;
   private Map<String, Integer> parameters = new LinkedHashMap<>();

   public MapTemplate() { }

   public MapTemplate(String id, PartyGameType type, long seed, Map<String, Integer> parameters) {
      this.id = id;
      this.gameId = type.id();
      this.seed = seed;
      this.parameters = new LinkedHashMap<>(parameters);
   }

   public int version() { return this.version; }
   public String id() { return this.id; }
   public String gameId() { return this.gameId; }
   public boolean enabled() { return this.enabled; }
   public boolean defaultTemplate() { return this.defaultTemplate; }
   public long seed() { return this.seed; }
   public Map<String, Integer> parameters() { return Map.copyOf(this.parameters); }
   public PartyGameType type() { return PartyGameType.byId(this.gameId); }
   public void setEnabled(boolean enabled) { this.enabled = enabled; }
   public void setDefaultTemplate(boolean value) { this.defaultTemplate = value; }
   public void setSeed(long seed) { this.seed = seed; }
   public void setParameter(String key, int value) { this.parameters.put(key, value); }
   public int parameter(String key, int fallback) { return this.parameters.getOrDefault(key, fallback); }

   public MapTemplate copy(String copyId) {
      MapTemplate copy = new MapTemplate(copyId, this.type(), this.seed, this.parameters);
      copy.enabled = this.enabled;
      return copy;
   }
}
