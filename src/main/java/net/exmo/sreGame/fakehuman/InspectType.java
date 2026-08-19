package net.exmo.sreGame.fakehuman;

public enum InspectType {
   TEMP("测体温", "ice", "伪人体温异常", 0.20),
   EYES("观察眼睛", "ender_eye", "伪人可能红眼", 0.30),
   TEETH("检查牙齿", "bone", "伪人牙齿过完美/金牙", 0.15),
   BACKGROUND("背景调查", "writable_book", "发现证件矛盾", 0.25);

   private final String display;
   private final String icon;
   private final String hint;
   private final double missRate;

   InspectType(String display, String icon, String hint, double missRate) {
      this.display = display;
      this.icon = icon;
      this.hint = hint;
      this.missRate = missRate;
   }

   public String display() {
      return this.display;
   }

   public String icon() {
      return this.icon;
   }

   public String hint() {
      return this.hint;
   }

   public double missRate() {
      return this.missRate;
   }
}
