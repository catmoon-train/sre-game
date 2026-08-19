package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.exmo.sreGame.youguess.YouGuessSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class YouGuessSetupGui {
   private YouGuessSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, container, room);
      boolean draw = room.isDrawGuess();
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color(draw ? "&d⌂ &f你画我猜设置" : "&d⌂ &f你建我猜设置")
      ));
   }

   private static void fill(GameContext ctx, SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      YouGuessSettings s = room.youGuessSettings();
      boolean draw = room.isDrawGuess();
      container.setItem(11, GuiItems.action("clock", "&f轮数 &e" + s.roundsLabel(room.size()), List.of(
         draw ? "&7默认等于人数，每人当一次画手" : "&7默认等于人数，每人当一次建造者",
         "&e点击切换 自动 / 1–20"
      ), "rounds"));
      container.setItem(13, GuiItems.action(draw ? "brush" : "wooden_axe",
         (draw ? "&f绘画时长 &e" : "&f建造时长 &e") + s.buildSeconds() + "s", List.of(
         draw ? "&7限时内边画边猜，用笔刷在白色画布上作画" : "&7限时内边建边猜",
         "&e点击切换 30–150"
      ), "build"));
      container.setItem(15, GuiItems.action("writable_book", "&f词库 &e" + room.wordPackLabel(), List.of(
         "&7当前 &f" + room.resolvedWords(ctx).size() + " &7词",
         "&e点击管理导入/导出"
      ), "words"));
      container.setItem(17, GuiItems.action(
         s.customTheme() ? "name_tag" : "paper",
         "&f自定主题 &e" + s.customThemeLabel(),
         List.of(
            "&7默认关：每轮从词库随机或三选一",
            draw ? "&7开启：画手聊天输入主题" : "&7开启：建造者聊天输入主题",
            "&7超时未输入则随机",
            "&e点击切换"
         ),
         "custom"
      ));
      container.setItem(19, GuiItems.action(
         s.pickFromThree() ? "map" : "paper",
         "&f三选一主题 &e" + s.pickFromThreeLabel(),
         List.of(
            "&7默认开：每轮从 3 个主题里挑",
            "&7各轮各人选项互不重复",
            "&7聊天输入 1/2/3 或右键物品",
            "&7自定主题开启时以自定为准",
            "&e点击切换"
         ),
         "pick3"
      ));
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
            fill(this.ctx, this.container, room);
            return;
         }
         YouGuessSettings s = room.youGuessSettings();
         switch (action) {
            case "rounds" -> {
               s.cycleRounds();
               fill(this.ctx, this.container, room);
            }
            case "build" -> {
               s.cycleBuildSeconds();
               fill(this.ctx, this.container, room);
            }
            case "words" -> WordPackGui.open(this.ctx, player, room, room.miniGameId());
            case "custom" -> {
               s.cycleCustomTheme();
               fill(this.ctx, this.container, room);
            }
            case "pick3" -> {
               s.cyclePickFromThree();
               fill(this.ctx, this.container, room);
            }
            default -> {
            }
         }
      }
   }
}
