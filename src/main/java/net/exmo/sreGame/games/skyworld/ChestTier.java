package net.exmo.sreGame.games.skyworld;

public enum ChestTier {
   BASIC("普通偏弱", "oak_planks"),
   NORMAL("标准", "chest"),
   OP("加强", "diamond_block");

   private final String label;
   private final String icon;

   ChestTier(String label, String icon) {
      this.label = label;
      this.icon = icon;
   }

   public String label() {
      return this.label;
   }

   public String icon() {
      return this.icon;
   }

   public ChestTier next() {
      ChestTier[] all = values();
      return all[(this.ordinal() + 1) % all.length];
   }

   public static ChestTier fromName(String name) {
      if (name == null || name.isBlank()) {
         return NORMAL;
      }
      try {
         return valueOf(name.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         return NORMAL;
      }
   }
}
