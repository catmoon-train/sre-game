package net.exmo.sreGame.games.fakehuman;

public enum Role {
   KEEPER("屋主", "&6"),
   HUMAN("真人", "&a"),
   IMPOSTOR("伪人", "&c");

   private final String display;
   private final String color;

   Role(String display, String color) {
      this.display = display;
      this.color = color;
   }

   public String display() {
      return this.display;
   }

   public String color() {
      return this.color;
   }

   public String labeled() {
      return this.color + this.display;
   }

   public boolean humanSide() {
      return this != IMPOSTOR;
   }
}
