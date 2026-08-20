package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.dig.DigToDeathSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class DigToDeathSetupGui {
   private DigToDeathSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&b⌂ &f掘一死战设置")
      ));
   }

   private static void fill(SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      DigToDeathSettings s = room.digToDeathSettings();
      container.setItem(11, GuiItems.action(s.variant().icon(), "&f变体 &e" + s.variant().label(), List.of(
         "&7铲子：效率五纯挖",
         "&7雪球：砸块破坏 + 打人击退",
         "&7铲子+雪球：砸地半径 2 球形破坏",
         "&e点击切换"
      ), "variant"));
      container.setItem(13, GuiItems.action("snow_block", "&f层数 &e" + s.layers(), List.of(
         "&7默认 3 层，最底层岩浆，上层雪块",
         "&7层间 4 格空气",
         "&e点击切换 2–6"
      ), "layers"));
      container.setItem(31, GuiItems.named("lava_bucket", "&f规则摘要", List.of(
         "&72–16 人，共享 32×32 雪台",
         "&7近战关闭，掉岩浆/掉出即淘汰",
         "&7最后存活一人获胜"
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
         DigToDeathSettings s = room.digToDeathSettings();
         switch (action) {
            case "variant" -> s.cycleVariant();
            case "layers" -> s.cycleLayers();
            default -> {
               return;
            }
         }
         fill(this.container, room);
      }
   }
}
