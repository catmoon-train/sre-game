package net.exmo.sreGame.fakehuman;

public enum Supply {
   STONE("驱逐之石", "ender_eye"),
   AMMO("弹药", "crossbow"),
   ROPE("绳子", "lead"),
   INSPECT("查验药剂", "spyglass");

   private final String display;
   private final String icon;

   Supply(String display, String icon) {
      this.display = display;
      this.icon = icon;
   }

   public String display() {
      return this.display;
   }

   public String icon() {
      return this.icon;
   }

   public static Supply random() {
      Supply[] all = values();
      return all[java.util.concurrent.ThreadLocalRandom.current().nextInt(all.length)];
   }
}
