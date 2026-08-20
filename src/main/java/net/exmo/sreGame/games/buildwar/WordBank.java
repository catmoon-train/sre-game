package net.exmo.sreGame.games.buildwar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import net.exmo.sreGame.SreGame;

public final class WordBank {
   private final Path file;
   private final List<String> words = new CopyOnWriteArrayList<>();

   public WordBank(Path configDir) {
      this.file = configDir.resolve("words.yml");
   }

   public void load() {
      this.words.clear();
      try {
         Files.createDirectories(this.file.getParent());
         if (!Files.exists(this.file)) {
            try (InputStream in = WordBank.class.getResourceAsStream("/words.yml")) {
               if (in != null) {
                  Files.copy(in, this.file);
               } else {
                  Files.writeString(this.file, defaultYaml(), StandardCharsets.UTF_8);
               }
            }
         }
         for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
            String word = parseLine(line);
            if (word != null && !this.words.contains(word)) {
               this.words.add(word);
            }
         }
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to load words.yml", e);
      }
      if (this.words.isEmpty()) {
         this.words.add("星空");
      }
   }

   public List<String> all() {
      return List.copyOf(this.words);
   }

   public boolean isEmpty() {
      return this.words.isEmpty();
   }

   public String random() {
      if (this.words.isEmpty()) {
         return "星空";
      }
      return this.words.get(ThreadLocalRandom.current().nextInt(this.words.size()));
   }

   public List<String> pickDistinct(int count) {
      return pickFrom(this.words, count);
   }

   public static List<String> pickUnique(List<String> source, Set<String> used, int count) {
      LinkedHashSet<String> seen = new LinkedHashSet<>();
      List<String> unused = new ArrayList<>();
      if (source != null) {
         for (String word : source) {
            if (word == null || word.isBlank() || !seen.add(word)) {
               continue;
            }
            if (used == null || !used.contains(word)) {
               unused.add(word);
            }
         }
      }
      Collections.shuffle(unused);
      List<String> picked = new ArrayList<>();
      int n = Math.max(0, count);
      for (int i = 0; i < Math.min(n, unused.size()); i++) {
         picked.add(unused.get(i));
      }
      if (picked.isEmpty()) {
         List<String> fallback = pickFrom(source, Math.max(1, n));
         picked.addAll(fallback.subList(0, Math.min(n, fallback.size())));
      }
      if (used != null) {
         used.addAll(picked);
      }
      return picked;
   }

   public static int indexOfChoice(String text, List<String> options) {
      if (text == null || options == null || options.isEmpty()) {
         return -1;
      }
      String guess = text.trim();
      if (guess.matches("[1-9]") ) {
         int i = Integer.parseInt(guess) - 1;
         return i >= 0 && i < options.size() ? i : -1;
      }
      for (int i = 0; i < options.size(); i++) {
         if (guess.equalsIgnoreCase(options.get(i))) {
            return i;
         }
      }
      return -1;
   }

   public static List<String> pickFrom(List<String> source, int count) {
      int n = Math.max(0, count);
      List<String> pool = new ArrayList<>(source == null ? List.of() : source);
      if (pool.isEmpty()) {
         pool.add("星空");
      }
      Collections.shuffle(pool);
      List<String> picked = new ArrayList<>(n);
      for (int i = 0; i < Math.min(n, pool.size()); i++) {
         picked.add(pool.get(i));
      }
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      while (picked.size() < n) {
         picked.add(pool.get(rng.nextInt(pool.size())));
      }
      return picked;
   }

   public boolean add(String word) {
      String cleaned = word == null ? "" : word.trim();
      if (cleaned.isEmpty() || cleaned.length() > 32 || this.words.contains(cleaned)) {
         return false;
      }
      this.words.add(cleaned);
      this.save();
      return true;
   }

   public boolean remove(String word) {
      boolean removed = this.words.remove(word);
      if (removed) {
         this.save();
      }
      return removed;
   }

   private void save() {
      StringBuilder sb = new StringBuilder();
      sb.append("# 建筑战争主题词库\nwords:\n");
      for (String word : this.words) {
         sb.append("  - ").append(word).append('\n');
      }
      try {
         Files.writeString(this.file, sb.toString(), StandardCharsets.UTF_8);
      } catch (IOException e) {
         SreGame.LOGGER.warn("Failed to save words.yml", e);
      }
   }

   private static String parseLine(String line) {
      if (line == null) {
         return null;
      }
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.equals("words:")) {
         return null;
      }
      if (trimmed.startsWith("- ")) {
         trimmed = trimmed.substring(2).trim();
      } else if (trimmed.startsWith("-")) {
         trimmed = trimmed.substring(1).trim();
      }
      if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
         trimmed = trimmed.substring(1, trimmed.length() - 1);
      }
      return trimmed.isEmpty() ? null : trimmed;
   }

   private static String defaultYaml() {
      return """
         # 建筑战争主题词库
         words:
           - 星空
           - 流星
           - 银河
           - 灯塔
           - 火车
           - 图书馆
           - 花园
           - 城堡
           - 港口
           - 雪山
           - 集市
           - 神殿
           - 瀑布
           - 村庄
           - 飞船
           - 迷宫
           - 温泉
           - 剧院
           - 桥梁
           - 风车
         """;
   }
}
