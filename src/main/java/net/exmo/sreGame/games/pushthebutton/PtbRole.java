package net.exmo.sreGame.games.pushthebutton;

public enum PtbRole {
   HUMAN("&a人类", "人类"),
   ALIEN("&c外星人", "外星人"),
   JESTER("&d小丑", "小丑");

   private final String colored;
   private final String plain;

   PtbRole(String colored, String plain) {
      this.colored = colored;
      this.plain = plain;
   }

   public String colored() {
      return this.colored;
   }

   public String plain() {
      return this.plain;
   }

   public boolean alienLike() {
      return this == ALIEN;
   }

   public boolean humanLike() {
      return this != ALIEN;
   }
}
