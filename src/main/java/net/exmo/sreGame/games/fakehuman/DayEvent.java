package net.exmo.sreGame.games.fakehuman;

public enum DayEvent {
   BLACKOUT("停电", "当晚无法查验，只能对话"),
   FEMA("FEMA 检查", "官方上门核对人数；若有误杀记录将公开"),
   RAIN("雨夜", "体温查验失效"),
   GROUP_KNOCK("集体敲门", "今日同时来两人上门"),
   CONFESS("自首者", "一名访客声称自己是伪人（真假不定）"),
   SHORTAGE("物资短缺", "容量临时-1，须先把一人打发回门外才能纳新");

   private final String display;
   private final String desc;

   DayEvent(String display, String desc) {
      this.display = display;
      this.desc = desc;
   }

   public String display() {
      return this.display;
   }

   public String desc() {
      return this.desc;
   }
}
