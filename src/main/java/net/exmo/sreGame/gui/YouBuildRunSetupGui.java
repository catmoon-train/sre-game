package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.buildrun.YouBuildRunSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class YouBuildRunSetupGui {
   private YouBuildRunSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&a⌂ &f你建我跑设置")
      ));
   }

   private static void fill(SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      YouBuildRunSettings s = room.youBuildRunSettings();
      container.setItem(10, GuiItems.action(s.scene().icon(), "&f场景 &e" + s.scene().label(), List.of(
         "&7默认：你建我猜同款房间",
         "&7可选：超级鸡马式长赛道",
         "&e点击切换"
      ), "scene"));
      container.setItem(12, GuiItems.action("clock", "&f建造时长 &e" + s.buildSeconds() + "s", List.of(
         "&7每人同时建造自己的跑酷",
         "&e点击切换 60 / 120 / 180 / 240"
      ), "build"));
      container.setItem(14, GuiItems.action("golden_boots", "&f自测时长 &e" + s.selfSeconds() + "s", List.of(
         "&7必须自己跑通，超时淘汰",
         "&7自测无生命限制",
         "&e点击切换 60 / 90 / 120 / 180"
      ), "self"));
      container.setItem(16, GuiItems.action("stone", "&f方块数量 &e" + s.blockLimit(), List.of(
         "&7另给镐铲斧、两组脚手架与红石件",
         "&e点击切换 32 / 64 / 128 / 256"
      ), "blocks"));
      container.setItem(28, GuiItems.action("clock", "&f交换时限 &e" + s.runSeconds() + "s", List.of(
         "&7互相跑图的时间限制",
         "&7超时未通关淘汰",
         "&e点击切换 60 / 90 / 120 / 180"
      ), "run"));
      container.setItem(30, GuiItems.action("redstone", "&f交换生命 &e" + s.lives(), List.of(
         "&7自测通过后互相跑图",
         "&7掉出扣命，最后活人获胜",
         "&e点击切换 1 / 2 / 3 / 5"
      ), "lives"));
      container.setItem(32, GuiItems.named("diamond_block", "&f规则摘要", List.of(
         "&72–8 人，金块起点、钻石块终点",
         "&7绿宝石记录点最多 3 个",
         "&7出生平台，其余地面为虚空",
         "&7自测 / 交换默认 120s，超时淘汰"
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
         YouBuildRunSettings s = room.youBuildRunSettings();
         switch (action) {
            case "scene" -> s.cycleScene();
            case "build" -> s.cycleBuildSeconds();
            case "self" -> s.cycleSelfSeconds();
            case "run" -> s.cycleRunSeconds();
            case "blocks" -> s.cycleBlockLimit();
            case "lives" -> s.cycleLives();
            default -> {
               return;
            }
         }
         fill(this.container, room);
      }
   }
}
