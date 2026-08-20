package net.exmo.sreGame.words;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.SreGame;
import net.exmo.sreGame.games.buildwar.WordBank;

public final class WordLibrary {
   public static final int MAX_PACKS = 10;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

   private final Path dir;
   private final WordBank server;

   public WordLibrary(Path configDir, WordBank server) {
      this.dir = configDir.resolve("player-banks");
      this.server = server;
   }

   public void load() {
      try {
         Files.createDirectories(this.dir);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to create player-banks dir", e);
      }
      this.server.load();
   }

   public WordBank server() {
      return this.server;
   }

   public List<WordPack> packsOf(UUID player) {
      return this.read(player).packs();
   }

   public WordPack get(UUID player, String packId) {
      if (packId == null) {
         return null;
      }
      for (WordPack pack : this.packsOf(player)) {
         if (packId.equals(pack.id())) {
            return pack;
         }
      }
      return null;
   }

   public WordPack saveNew(UUID player, String name, List<String> words) {
      FileData data = this.read(player);
      if (data.packs().size() >= MAX_PACKS) {
         return null;
      }
      WordPack pack = new WordPack(UUID.randomUUID().toString(), name, words);
      data.add(pack);
      this.write(player, data);
      return pack;
   }

   public boolean delete(UUID player, String packId) {
      FileData data = this.read(player);
      boolean removed = data.removeId(packId);
      if (removed) {
         this.write(player, data);
      }
      return removed;
   }

   private FileData read(UUID player) {
      Path file = this.file(player);
      if (!Files.exists(file)) {
         return new FileData();
      }
      try {
         FileData data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FileData.class);
         return data == null ? new FileData() : data.normalized();
      } catch (Exception e) {
         SreGame.LOGGER.warn("Failed to read word packs for {}", player, e);
         return new FileData();
      }
   }

   private void write(UUID player, FileData data) {
      try {
         Files.createDirectories(this.dir);
         Files.writeString(this.file(player), GSON.toJson(data.normalized()), StandardCharsets.UTF_8);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to save word packs for {}", player, e);
      }
   }

   private Path file(UUID player) {
      return this.dir.resolve(player.toString() + ".json");
   }

   public static final class FileData {
      private List<PackJson> packs = new ArrayList<>();

      public List<WordPack> packs() {
         List<WordPack> list = new ArrayList<>();
         if (this.packs == null) {
            return list;
         }
         for (PackJson json : this.packs) {
            if (json != null) {
               list.add(json.toPack());
            }
         }
         return list;
      }

      void add(WordPack pack) {
         if (this.packs == null) {
            this.packs = new ArrayList<>();
         }
         this.packs.add(PackJson.from(pack));
      }

      boolean removeId(String packId) {
         if (this.packs == null) {
            return false;
         }
         return this.packs.removeIf(json -> json != null && packId.equals(json.id));
      }

      FileData normalized() {
         FileData copy = new FileData();
         for (WordPack pack : this.packs()) {
            copy.packs.add(PackJson.from(pack));
         }
         while (copy.packs.size() > MAX_PACKS) {
            copy.packs.remove(copy.packs.size() - 1);
         }
         return copy;
      }
   }

   public static final class PackJson {
      public String id;
      public String name;
      public List<String> words;

      static PackJson from(WordPack pack) {
         PackJson json = new PackJson();
         json.id = pack.id();
         json.name = pack.name();
         json.words = new ArrayList<>(pack.words());
         return json;
      }

      WordPack toPack() {
         return new WordPack(this.id, this.name, this.words);
      }
   }
}
