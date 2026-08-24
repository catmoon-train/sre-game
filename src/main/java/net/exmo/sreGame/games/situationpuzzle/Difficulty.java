package net.exmo.sreGame.games.situationpuzzle;

/**
 * 海龟汤题目难度。
 */
public enum Difficulty {
   EASY("简单", "⭐⭐"),
   NORMAL("普通", "⭐⭐⭐"),
   HARD("困难", "⭐⭐⭐⭐"),
   HELL("地狱", "⭐⭐⭐⭐⭐");

   private final String label;
   private final String stars;

   Difficulty(String label, String stars) {
      this.label = label;
      this.stars = stars;
   }

   public String label() {
      return this.label;
   }

   public String stars() {
      return this.stars;
   }

   public Difficulty next() {
      Difficulty[] all = values();
      return all[(ordinal() + 1) % all.length];
   }
}
