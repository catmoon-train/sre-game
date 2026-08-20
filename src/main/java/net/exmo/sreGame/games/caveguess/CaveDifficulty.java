package net.exmo.sreGame.games.caveguess;

public enum CaveDifficulty {
   ALL("全部"),
   EASY("简单"),
   NORMAL("普通"),
   HARD("困难");

   private final String label;

   CaveDifficulty(String label) {
      this.label = label;
   }

   public String label() {
      return this.label;
   }

   public boolean matches(String wordDifficulty) {
      if (this == ALL) {
         return true;
      }
      String raw = wordDifficulty == null || wordDifficulty.isBlank() ? "normal" : wordDifficulty.trim();
      return this.name().equalsIgnoreCase(raw);
   }

   public CaveDifficulty next() {
      CaveDifficulty[] all = values();
      return all[(this.ordinal() + 1) % all.length];
   }
}
