package net.exmo.sreGame.util;

public final class WordHint {
   private WordHint() {
   }

   public static int count(String word) {
      if (word == null) {
         return 0;
      }
      return word.trim().length();
   }

   public static String label(String word) {
      return count(word) + " 个字";
   }
}
