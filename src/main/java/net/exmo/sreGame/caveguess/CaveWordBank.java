package net.exmo.sreGame.caveguess;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.SreGame;
import net.exmo.sreGame.room.GameRoom;

public final class CaveWordBank {
   private final Path file;
   private final List<CaveWord> words = new CopyOnWriteArrayList<>();
   private final Map<String, CaveWord> byWord = new LinkedHashMap<>();

   public CaveWordBank(Path configDir) {
      this.file = configDir.resolve("cave-words.yml");
   }

   public void load() {
      this.words.clear();
      this.byWord.clear();
      try {
         Files.createDirectories(this.file.getParent());
         if (!Files.exists(this.file)) {
            try (InputStream in = CaveWordBank.class.getResourceAsStream("/cave-words.yml")) {
               if (in != null) {
                  Files.copy(in, this.file);
               } else {
                  Files.writeString(this.file, defaultYaml(), StandardCharsets.UTF_8);
               }
            }
         }
         for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
            CaveWord word = parseLine(line);
            if (word != null && !word.word().isBlank() && !this.byWord.containsKey(word.word())) {
               this.words.add(word);
               this.byWord.put(word.word(), word);
            }
         }
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to load cave-words.yml", e);
      }
      if (this.words.isEmpty()) {
         CaveWord fallback = CaveWord.plain("苦力怕");
         this.words.add(fallback);
         this.byWord.put(fallback.word(), fallback);
      }
   }

   public List<CaveWord> all() {
      return List.copyOf(this.words);
   }

   public List<String> plainTexts() {
      List<String> out = new ArrayList<>(this.words.size());
      for (CaveWord word : this.words) {
         out.add(word.word());
      }
      return out;
   }

   public List<CaveWord> resolved(GameRoom room) {
      CaveDifficulty difficulty = room == null ? CaveDifficulty.ALL : room.caveSettings().difficulty();
      List<CaveWord> source;
      if (room != null && room.hasCustomWords()) {
         source = new ArrayList<>();
         for (String raw : room.customWords()) {
            CaveWord found = this.byWord.get(raw);
            source.add(found != null ? found : CaveWord.plain(raw));
         }
      } else {
         source = this.words;
      }
      List<CaveWord> out = new ArrayList<>();
      for (CaveWord word : source) {
         if (difficulty.matches(word.difficulty())) {
            out.add(word);
         }
      }
      return out.isEmpty() ? List.copyOf(source) : out;
   }

   public List<CaveWord> tunePool(List<CaveWord> source) {
      List<CaveWord> out = new ArrayList<>();
      for (CaveWord word : source) {
         if (word.tune()) {
            out.add(word);
         }
      }
      return out;
   }

   public CaveWord pick(List<CaveWord> pool, java.util.Set<String> used, boolean tuneOnly) {
      List<CaveWord> candidates = new ArrayList<>();
      for (CaveWord word : pool) {
         if (tuneOnly != word.tune()) {
            continue;
         }
         if (used != null && used.contains(word.word())) {
            continue;
         }
         candidates.add(word);
      }
      if (candidates.isEmpty()) {
         for (CaveWord word : pool) {
            if (tuneOnly == word.tune()) {
               candidates.add(word);
            }
         }
      }
      if (candidates.isEmpty()) {
         return null;
      }
      return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
   }

   public List<String> choicesFor(CaveWord word, List<CaveWord> pool) {
      List<String> options = new ArrayList<>();
      options.add(word.word());
      for (String decoy : word.decoys()) {
         if (!decoy.isBlank() && !options.contains(decoy) && options.size() < 4) {
            options.add(decoy);
         }
      }
      List<CaveWord> extras = new ArrayList<>();
      for (CaveWord other : pool) {
         if (other.tune() && !other.word().equals(word.word()) && !options.contains(other.word())) {
            extras.add(other);
         }
      }
      Collections.shuffle(extras);
      for (CaveWord extra : extras) {
         if (options.size() >= 4) {
            break;
         }
         options.add(extra.word());
      }
      while (options.size() < 4) {
         options.add("？？？");
      }
      Collections.shuffle(options);
      return options.subList(0, 4);
   }

   private static CaveWord parseLine(String line) {
      if (line == null) {
         return null;
      }
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
         return null;
      }
      if (trimmed.startsWith("- ")) {
         trimmed = trimmed.substring(2).trim();
      }
      String[] parts = trimmed.split("\\|");
      String word = parts[0].trim();
      if (word.isEmpty()) {
         return null;
      }
      List<String> banned = new ArrayList<>();
      List<String> decoys = new ArrayList<>();
      String category = "misc";
      String difficulty = "normal";
      for (int i = 1; i < parts.length; i++) {
         String piece = parts[i].trim();
         int split = piece.indexOf(':');
         if (split <= 0) {
            continue;
         }
         String key = piece.substring(0, split).trim().toLowerCase(Locale.ROOT);
         String value = piece.substring(split + 1).trim();
         switch (key) {
            case "banned" -> banned.addAll(splitCsv(value));
            case "cat", "category" -> category = value;
            case "diff", "difficulty" -> difficulty = value;
            case "decoys", "decoy" -> decoys.addAll(splitCsv(value));
            default -> {
            }
         }
      }
      if (!banned.contains(word)) {
         banned.add(word);
      }
      return new CaveWord(word, banned, category, difficulty, decoys);
   }

   private static List<String> splitCsv(String value) {
      List<String> out = new ArrayList<>();
      if (value == null || value.isBlank()) {
         return out;
      }
      for (String piece : value.split("[,，]")) {
         String item = piece.trim();
         if (!item.isEmpty() && !out.contains(item)) {
            out.add(item);
         }
      }
      return out;
   }

   private static String defaultYaml() {
      return "# 洞穴猜猜乐词库\n苦力怕|banned:苦力,怕,爆炸,绿色|cat:mc_mob|diff:easy\n";
   }
}
