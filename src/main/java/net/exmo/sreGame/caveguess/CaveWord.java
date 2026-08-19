package net.exmo.sreGame.caveguess;

import java.util.ArrayList;
import java.util.List;

public record CaveWord(String word, List<String> banned, String category, String difficulty, List<String> decoys) {
   public CaveWord {
      word = word == null ? "" : word.trim();
      banned = List.copyOf(banned == null ? List.of() : banned);
      category = category == null || category.isBlank() ? "misc" : category.trim();
      difficulty = difficulty == null || difficulty.isBlank() ? "normal" : difficulty.trim().toLowerCase();
      decoys = List.copyOf(decoys == null ? List.of() : decoys);
   }

   public boolean tune() {
      return "tune".equalsIgnoreCase(this.category);
   }

   public List<String> bannedForDisplay() {
      List<String> out = new ArrayList<>();
      for (String item : this.banned) {
         if (item != null && !item.isBlank() && !item.equals(this.word) && !out.contains(item)) {
            out.add(item);
         }
      }
      return out;
   }

   public static CaveWord plain(String word) {
      String cleaned = word == null ? "" : word.trim();
      return new CaveWord(cleaned, List.of(cleaned), "custom", "normal", List.of());
   }
}
