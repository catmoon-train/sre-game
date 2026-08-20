package net.exmo.sreGame.games.parkour;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.exmo.sreGame.SreGame;
import net.minecraft.server.level.ServerPlayer;

public final class ParkourLeaderboard {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private static final TypeToken<Map<String, Entry>> TYPE = new TypeToken<>() {
   };

   private final Path file;
   private final Map<String, Entry> scores = new LinkedHashMap<>();

   public ParkourLeaderboard(Path configDir) {
      this.file = configDir.resolve("parkour-scores.json");
   }

   public void load() {
      this.scores.clear();
      if (!Files.exists(this.file)) {
         return;
      }
      try {
         Map<String, Entry> data = GSON.fromJson(Files.readString(this.file, StandardCharsets.UTF_8), TYPE.getType());
         if (data != null) {
            this.scores.putAll(data);
         }
      } catch (Exception e) {
         SreGame.LOGGER.warn("Failed to load parkour scores", e);
      }
   }

   public void record(ServerPlayer player, int score, long timeMs) {
      if (player == null || score <= 0) {
         return;
      }
      String id = player.getUUID().toString();
      Entry old = this.scores.get(id);
      if (old != null && old.score > score) {
         return;
      }
      if (old != null && old.score == score && old.timeMs <= timeMs) {
         return;
      }
      this.scores.put(id, new Entry(player.getGameProfile().getName(), score, timeMs));
      this.save();
   }

   public Entry get(UUID uuid) {
      return this.scores.get(uuid.toString());
   }

   public List<Map.Entry<String, Entry>> top(int n) {
      List<Map.Entry<String, Entry>> list = new ArrayList<>(this.scores.entrySet());
      list.sort(Comparator.<Map.Entry<String, Entry>>comparingInt(e -> e.getValue().score).reversed()
         .thenComparingLong(e -> e.getValue().timeMs));
      return list.subList(0, Math.min(n, list.size()));
   }

   private void save() {
      try {
         Files.createDirectories(this.file.getParent());
         Files.writeString(this.file, GSON.toJson(this.scores), StandardCharsets.UTF_8);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to save parkour scores", e);
      }
   }

   public static final class Entry {
      public String name;
      public int score;
      public long timeMs;

      public Entry() {
      }

      public Entry(String name, int score, long timeMs) {
         this.name = name;
         this.score = score;
         this.timeMs = timeMs;
      }
   }
}
