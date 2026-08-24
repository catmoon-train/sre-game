package net.exmo.sreGame.games.dig;

public enum DigVariant {
   SHOVEL("效率五铲子纯挖", "diamond_shovel"),
   SNOWBALL("雪球互相丢", "snowball"),
   BOTH("铲子+雪球", "iron_shovel");

   private final String label;
   private final String icon;

   DigVariant(String label, String icon) {
      this.label = label;
      this.icon = icon;
   }

   public String label() {
      return this.label;
   }

   public String icon() {
      return this.icon;
   }

   public boolean shovel() {
      return this != SNOWBALL;
   }

   public boolean snowballs() {
      return this != SHOVEL;
   }

   public int snowRadius() {
      return this == BOTH ? 1 : 0;
   }

   public DigVariant next() {
      DigVariant[] all = values();
      return all[(this.ordinal() + 1) % all.length];
   }

   public static DigVariant fromName(String name) {
      if (name == null) {
         return SHOVEL;
      }
      try {
         return valueOf(name);
      } catch (IllegalArgumentException e) {
         return SHOVEL;
      }
   }
}
