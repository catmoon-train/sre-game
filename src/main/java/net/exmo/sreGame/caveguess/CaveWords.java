package net.exmo.sreGame.caveguess;

import java.util.List;

public final class CaveWords {
   private CaveWords() {
   }

   public static String normalize(String text) {
      if (text == null) {
         return "";
      }
      StringBuilder out = new StringBuilder(text.length());
      for (int i = 0; i < text.length(); ) {
         int cp = text.codePointAt(i);
         i += Character.charCount(cp);
         if (cp == ' ' || cp == '\t' || cp == '\u3000' || cp == '\n' || cp == '\r') {
            continue;
         }
         if (cp >= 0xFF01 && cp <= 0xFF5E) {
            cp = cp - 0xFEE0;
         }
         out.appendCodePoint(Character.toLowerCase(cp));
      }
      return out.toString();
   }

   public static boolean matches(String guess, String word) {
      String a = normalize(guess);
      String b = normalize(word);
      return !a.isEmpty() && a.equals(b);
   }

   public static boolean containsAny(String text, String word, List<String> banned) {
      String hay = normalize(text);
      if (hay.isEmpty()) {
         return false;
      }
      String target = normalize(word);
      if (!target.isEmpty() && hay.contains(target)) {
         return true;
      }
      if (banned != null) {
         for (String item : banned) {
            String needle = normalize(item);
            if (!needle.isEmpty() && hay.contains(needle)) {
               return true;
            }
         }
      }
      return false;
   }

   public static boolean clueHitsTarget(String clue, String word) {
      String c = normalize(clue);
      String w = normalize(word);
      if (c.isEmpty() || w.isEmpty()) {
         return false;
      }
      if (isMostlyLatin(w)) {
         return c.contains(w);
      }
      for (int i = 0; i < w.length(); ) {
         int cp = w.codePointAt(i);
         int n = Character.charCount(cp);
         if (c.contains(w.substring(i, i + n))) {
            return true;
         }
         i += n;
      }
      return false;
   }

   public static String wrap(String text, int width) {
      if (text == null || text.isEmpty() || width < 4) {
         return text == null ? "" : text;
      }
      StringBuilder out = new StringBuilder();
      int col = 0;
      for (int i = 0; i < text.length(); ) {
         int cp = text.codePointAt(i);
         int n = Character.charCount(cp);
         if (cp == '\n') {
            out.append('\n');
            col = 0;
            i += n;
            continue;
         }
         if (col >= width) {
            out.append('\n');
            col = 0;
         }
         out.appendCodePoint(cp);
         col++;
         i += n;
      }
      return out.toString();
   }

   private static boolean isMostlyLatin(String normalized) {
      int latin = 0;
      int total = 0;
      for (int i = 0; i < normalized.length(); ) {
         int cp = normalized.codePointAt(i);
         i += Character.charCount(cp);
         total++;
         if ((cp >= 'a' && cp <= 'z') || (cp >= '0' && cp <= '9')) {
            latin++;
         }
      }
      return total > 0 && latin * 2 >= total;
   }
}
