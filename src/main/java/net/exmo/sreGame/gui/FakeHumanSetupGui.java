package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.fakehuman.FakeHumanSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class FakeHumanSetupGui {
   private FakeHumanSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&c⌂ &f谁是伪人设置")
      ));
   }

   private static void fill(SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      FakeHumanSettings s = room.fakeHumanSettings();
      container.setItem(12, GuiItems.action("clock", "&f天数 &e" + s.days(), List.of(
         "&7默认 7 天，约 20–30 分钟",
         "&e点击切换 5 / 6 / 7"
      ), "days"));
      container.setItem(14, GuiItems.action("sunflower", "&f白天时长 &e" + s.daySeconds() + "s", List.of(
         "&7夜晚固定 60 秒结算",
         "&e点击切换 180 / 240 / 300"
      ), "day"));
      container.setItem(31, GuiItems.named("iron_door", "&f规则摘要", List.of(
         "&74–8 人，房间房主即屋主",
         "&7每天从未到访池抽 1 人上门（集体敲门 2 人）",
         "&7证件出示才看；补给请进后入库",
         "&7非屋主死亡后改头换面再访；屋主死亡结束"
      )));
      SettingsArchive.paint(container);
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
         if (SettingsArchive.handle(this.ctx, player, room, action)) {
            fill(this.container, room);
            return;
         }
         FakeHumanSettings s = room.fakeHumanSettings();
         switch (action) {
            case "days" -> {
               s.cycleDays();
               fill(this.container, room);
            }
            case "day" -> {
               s.cycleDaySeconds();
               fill(this.container, room);
            }
            default -> {
            }
         }
      }
   }
}
