package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;

public final class SettingsArchive {
   public static final String SAVE = "archive_save";
   public static final String LOAD = "archive_load";

   private SettingsArchive() {
   }

   public static void paint(SimpleContainer container) {
      paint(container, 45, 53);
   }

   public static void paint(SimpleContainer container, int saveSlot, int loadSlot) {
      container.setItem(saveSlot, GuiItems.action("writable_book", "&a保存到档案", List.of(
         "&7覆盖你在该模式的个人设置",
         "&e点击保存"
      ), SAVE));
      container.setItem(loadSlot, GuiItems.action("book", "&e从档案加载", List.of(
         "&7读取你上次保存的该模式设置",
         "&e点击加载"
      ), LOAD));
   }

   public static boolean handle(GameContext ctx, ServerPlayer player, GameRoom room, String action) {
      if (SAVE.equals(action)) {
         ctx.profiles().save(player, room);
         ctx.send(player, "&a已保存当前设置为个人档案。");
         return true;
      }
      if (LOAD.equals(action)) {
         if (ctx.profiles().load(player, room)) {
            ctx.send(player, "&a已从档案加载设置。");
         } else {
            ctx.send(player, "&c没有该模式的档案。");
         }
         return true;
      }
      return false;
   }
}
