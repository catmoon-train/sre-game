package net.exmo.sreGame.room;

public enum RoomChatMode {
   ROOM_ONLY("只收房间", "book",
      "&7本房只能看到本房聊天",
      "&7大厅和其他房间都看不到本房"),
   NO_LEAK("房间内不外放", "hopper",
      "&7本房能看到大厅聊天",
      "&7大厅看不到本房发言"),
   ALL("全部", "bell",
      "&7本房与全服互通",
      "&7大厅和其他「全部」房间都能看到");

   private final String label;
   private final String icon;
   private final String lore1;
   private final String lore2;

   RoomChatMode(String label, String icon, String lore1, String lore2) {
      this.label = label;
      this.icon = icon;
      this.lore1 = lore1;
      this.lore2 = lore2;
   }

   public String label() {
      return this.label;
   }

   public String icon() {
      return this.icon;
   }

   public String lore1() {
      return this.lore1;
   }

   public String lore2() {
      return this.lore2;
   }

   public RoomChatMode next() {
      RoomChatMode[] all = values();
      return all[(this.ordinal() + 1) % all.length];
   }
}
