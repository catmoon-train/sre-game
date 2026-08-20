package net.exmo.sreGame.games.buildrun;

public enum BuildRunScene {
   PLOT("房间（你建我猜）", "smooth_stone"),
   TRACK("赛道（超级鸡马）", "cooked_chicken");

   private final String label;
   private final String icon;

   BuildRunScene(String label, String icon) {
      this.label = label;
      this.icon = icon;
   }

   public String label() {
      return this.label;
   }

   public String icon() {
      return this.icon;
   }

   public BuildRunScene next() {
      return this == PLOT ? TRACK : PLOT;
   }

   public static BuildRunScene fromName(String name) {
      if ("TRACK".equalsIgnoreCase(name)) {
         return TRACK;
      }
      return PLOT;
   }
}
