package net.exmo.sreGame.words;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WordPack {
   private final String id;
   private String name;
   private final List<String> words = new ArrayList<>();

   public WordPack(String id, String name, List<String> words) {
      this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
      this.name = name == null || name.isBlank() ? "未命名词库" : name.trim();
      if (words != null) {
         for (String word : words) {
            if (word != null && !word.isBlank() && !this.words.contains(word.trim())) {
               this.words.add(word.trim());
            }
         }
      }
   }

   public String id() {
      return this.id;
   }

   public String name() {
      return this.name;
   }

   public void setName(String name) {
      if (name != null && !name.isBlank()) {
         this.name = name.trim();
      }
   }

   public List<String> words() {
      return this.words;
   }
}
