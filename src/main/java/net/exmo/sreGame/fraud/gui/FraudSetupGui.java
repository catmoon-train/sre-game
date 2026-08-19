package net.exmo.sreGame.fraud.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.fraud.FraudMasterSettings;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.RoomPanelGui;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class FraudSetupGui {
   private FraudSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&6⌂ &f诈骗大师设置")
      ));
   }

   private static void fill(SimpleContainer container, GameRoom room) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      FraudMasterSettings s = room.fraudSettings();
      container.setItem(11, GuiItems.action(s.busyMode() ? "redstone" : "glowstone",
         "&f通话模式 &e" + s.callModeLabel(),
         List.of(
            "&7开放：通话中仍可来电，接听会改成一对一",
            "&7占线：对方通话中不会响铃",
            "&e点击切换"
         ), "busy"));
      container.setItem(13, GuiItems.action(s.doubleRound() ? "nether_star" : "iron_nugget",
         "&f双倍回合 &e" + s.onOff(s.doubleRound()),
         List.of("&7随机一回合得分 ×2", "&e点击切换"), "double"));
      container.setItem(15, GuiItems.action(s.callTax() ? "gold_nugget" : "iron_nugget",
         "&f通话税 &e" + s.onOff(s.callTax()),
         List.of("&7每次拨号 -0.5 分（四舍五入）", "&e点击切换"), "tax"));
      container.setItem(29, GuiItems.action(s.anonymousVote() ? "ender_eye" : "ender_pearl",
         "&f匿名投票 &e" + s.onOff(s.anonymousVote()),
         List.of("&7投票扣分不公布谁投了谁", "&e点击切换"), "anon"));
      container.setItem(31, GuiItems.named("recovery_compass", "&f规则摘要", List.of(
         "&74–8 人，8 回合从小游戏池随机 + 终局",
         "&7小屋南墙有画板（你画我猜）",
         "&7电话接通后才能说话",
         "&7聊天仅同一通话可见",
         "&7分数可负，无人淘汰"
      )));
      net.exmo.sreGame.gui.SettingsArchive.paint(container);
      container.setItem(49, GuiItems.action("barrier", "&c返回房间", List.of(), "back"));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final SimpleContainer container;

      Menu(int syncId, Inventory playerInv, SimpleContainer container, GameContext ctx, ServerPlayer viewer) {
         super(syncId, playerInv, container, 6, viewer);
         this.ctx = ctx;
         this.container = container;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         String action = GuiItems.actionTag(this.getSlot(slotId).getItem());
         GameRoom room = this.ctx.rooms().getByPlayer(player.getUUID());
         if ("back".equals(action)) {
            RoomPanelGui.open(this.ctx, player);
            return;
         }
         if (action == null || room == null || !room.isHost(player.getUUID())) {
            return;
         }
         if (net.exmo.sreGame.gui.SettingsArchive.handle(this.ctx, player, room, action)) {
            fill(this.container, room);
            return;
         }
         FraudMasterSettings s = room.fraudSettings();
         switch (action) {
            case "busy" -> s.cycleBusyMode();
            case "double" -> s.cycleDoubleRound();
            case "tax" -> s.cycleCallTax();
            case "anon" -> s.cycleAnonymousVote();
            default -> {
               return;
            }
         }
         fill(this.container, room);
      }
   }
}
