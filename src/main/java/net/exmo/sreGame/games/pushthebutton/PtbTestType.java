package net.exmo.sreGame.games.pushthebutton;

public enum PtbTestType {
   WRITING("写作中心", "writable_book", 45),
   OPINION("意见吧", "oak_sign", 26),
   DELIB("商议泉", "lectern", 30),
   DRAWING("绘画板", "painting", 50),
   BIO("生物扫描", "spyglass", 40);

   private final String label;
   private final String icon;
   private final int seconds;

   PtbTestType(String label, String icon, int seconds) {
      this.label = label;
      this.icon = icon;
      this.seconds = seconds;
   }

   public String label() {
      return this.label;
   }

   public String icon() {
      return this.icon;
   }

   public int seconds() {
      return this.seconds;
   }
}
