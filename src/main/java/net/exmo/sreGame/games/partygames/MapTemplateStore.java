package net.exmo.sreGame.games.partygames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.SreGame;

public final class MapTemplateStore {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private static final TypeToken<List<MapTemplate>> TYPE = new TypeToken<>() { };
   private final Path file;
   private final Map<String, MapTemplate> templates = new LinkedHashMap<>();
   private final Map<PartyGameType, MapGenerator> generators = new LinkedHashMap<>();

   public MapTemplateStore(Path configDir) {
      this.file = configDir.resolve("maps.json");
      for (PartyGameType type : PartyGameType.values()) this.generators.put(type, new PartyMapGenerator(type));
   }

   public void load() {
      this.templates.clear();
      try {
         Files.createDirectories(this.file.getParent());
         if (Files.exists(this.file)) {
            List<MapTemplate> values = GSON.fromJson(Files.readString(this.file, StandardCharsets.UTF_8), TYPE.getType());
            if (values != null) for (MapTemplate template : values) addLoaded(template);
         }
         for (PartyGameType type : PartyGameType.values()) {
            if (list(type).isEmpty()) {
               MapTemplate template = new MapTemplate(type.id() + "-default", type,
                  Integer.toUnsignedLong(type.id().hashCode()), generator(type).defaultParameters());
               template.setDefaultTemplate(true);
               this.templates.put(template.id(), template);
            }
         }
         save();
      } catch (Exception e) {
         SreGame.LOGGER.warn("Failed to load party-game map templates", e);
      }
   }

   public void save() {
      try {
         Files.createDirectories(this.file.getParent());
         Files.writeString(this.file, GSON.toJson(new ArrayList<>(this.templates.values())), StandardCharsets.UTF_8);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to save party-game map templates", e);
      }
   }

   public MapGenerator generator(PartyGameType type) { return this.generators.get(type); }
   public Collection<MapGenerator> generators() { return List.copyOf(this.generators.values()); }
   public MapTemplate get(String id) { return id == null ? null : this.templates.get(id); }

   public List<MapTemplate> list(PartyGameType type) {
      List<MapTemplate> out = new ArrayList<>();
      for (MapTemplate template : this.templates.values()) if (template.type() == type) out.add(template);
      out.sort(Comparator.comparing(MapTemplate::id));
      return out;
   }

   public MapTemplate choose(PartyGameType type, String requestedId) {
      MapTemplate requested = get(requestedId);
      if (requested != null && requested.enabled() && requested.type() == type) return requested.copy(requested.id());
      List<MapTemplate> options = list(type).stream().filter(MapTemplate::enabled).toList();
      if (options.isEmpty()) return null;
      for (MapTemplate option : options) if (option.defaultTemplate()) return option.copy(option.id());
      MapTemplate selected = options.get(ThreadLocalRandom.current().nextInt(options.size()));
      return selected.copy(selected.id());
   }

   public boolean create(PartyGameType type, String id) {
      if (type == null || !validId(id) || this.templates.containsKey(id)) return false;
      this.templates.put(id, new MapTemplate(id, type, ThreadLocalRandom.current().nextLong(), generator(type).defaultParameters()));
      save();
      return true;
   }

   public boolean delete(String id) {
      MapTemplate old = get(id);
      if (old == null || old.defaultTemplate()) return false;
      this.templates.remove(id);
      save();
      return true;
   }

   public boolean duplicate(String id, String copyId) {
      MapTemplate source = get(id);
      if (source == null || !validId(copyId) || this.templates.containsKey(copyId)) return false;
      this.templates.put(copyId, source.copy(copyId));
      save();
      return true;
   }

   public boolean toggle(String id) {
      MapTemplate template = get(id);
      if (template == null) return false;
      template.setEnabled(!template.enabled());
      save();
      return true;
   }

   public boolean setDefault(String id) {
      MapTemplate target = get(id);
      if (target == null) return false;
      for (MapTemplate template : list(target.type())) template.setDefaultTemplate(template == target);
      save();
      return true;
   }

   public boolean setSeed(String id, long seed) {
      MapTemplate template = get(id);
      if (template == null) return false;
      template.setSeed(seed);
      save();
      return true;
   }

   public boolean setParameter(String id, String key, int value) {
      MapTemplate template = get(id);
      MapGenerator generator = template == null ? null : generator(template.type());
      if (template == null || generator == null || key == null || !generator.defaultParameters().containsKey(key)) return false;
      int old = template.parameter(key, generator.defaultParameters().get(key));
      template.setParameter(key, value);
      StringBuilder reason = new StringBuilder();
      if (!generator.validate(template, reason)) { template.setParameter(key, old); return false; }
      save();
      return true;
   }

   private void addLoaded(MapTemplate template) {
      if (template == null || !validId(template.id()) || template.type() == null || this.templates.containsKey(template.id())) return;
      StringBuilder reason = new StringBuilder();
      if (!generator(template.type()).validate(template, reason)) {
         SreGame.LOGGER.warn("Ignoring invalid party map {}: {}", template.id(), reason);
         return;
      }
      this.templates.put(template.id(), template);
   }

   private static boolean validId(String id) { return id != null && id.matches("[a-z0-9_-]{1,40}"); }
}
