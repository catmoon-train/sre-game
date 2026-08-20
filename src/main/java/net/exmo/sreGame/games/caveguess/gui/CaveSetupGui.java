package net.exmo.sreGame.games.caveguess.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.caveguess.CaveGuessersSettings;
import net.exmo.sreGame.games.caveguess.CaveMode;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.gui.RoomPanelGui;
import net.exmo.sreGame.gui.SettingsArchive;
import net.exmo.sreGame.gui.WordPackGui;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class CaveSetupGui {
   private CaveSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&d⌂ &f洞穴猜猜乐设置")
      ));
   }

   private static void fill(GameContext ctx, SimpleContainer container, GameRoom room) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      CaveGuessersSettings s = room.caveSettings();
      int slot = 10;
      for (CaveMode mode : CaveMode.values()) {
         int n = s.rounds(mode);
         container.setItem(slot, GuiItems.action(mode.icon(),
            "&f" + mode.display() + " &e×" + n,
            List.of("&7点击切换 0–5 轮", n == 0 ? "&c该模式关闭" : "&a已加入赛程"),
            "mode", "value", mode.name()));
         slot++;
         if (slot == 15) {
            slot = 19;
         }
      }
      container.setItem(28, GuiItems.action("compass", "&f难度 &e" + s.difficulty().label(),
         List.of("&7过滤词库难度", "&e点击切换"), "diff"));
      container.setItem(30, GuiItems.action(s.freeTuneGuess() ? "writable_book" : "jukebox",
         "&f曲调答题 &e" + s.freeTuneLabel(),
         List.of("&7四选一猜对 +2", "&7自由打字猜对 +3", "&e点击切换"), "tune"));
      container.setItem(32, GuiItems.action("writable_book", "&f词库 &e" + room.wordPackLabel(),
         List.of("&7洞穴词 " + ctx.caveWords().resolved(room).size() + " 条",
            "&7不自定义时使用洞穴词库（含禁用词）",
            "&e点击管理导入/导出"), "words"));
      container.setItem(40, GuiItems.named("map", "&f赛程", List.of("&7" + s.scheduleSummary(),
         "&7共 " + s.totalRounds() + " 轮")));
      SettingsArchive.paint(container);
      container.setItem(49, GuiItems.action("barrier", "&c返回房间", List.of(), "back"));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final GameContext ctx;
      private final SimpleContainer container;

      Menu(int syncId, Inventory inv, SimpleContainer container, GameContext ctx, ServerPlayer viewer) {
         super(syncId, inv, container, 6, viewer);
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
            fill(this.ctx, this.container, room);
            return;
         }
         CaveGuessersSettings s = room.caveSettings();
         switch (action) {
            case "mode" -> {
               try {
                  s.cycleRounds(CaveMode.valueOf(GuiItems.extraTag(this.getSlot(slotId).getItem(), "value")));
               } catch (IllegalArgumentException ignored) {
               }
               fill(this.ctx, this.container, room);
            }
            case "diff" -> {
               s.cycleDifficulty();
               fill(this.ctx, this.container, room);
            }
            case "tune" -> {
               s.cycleFreeTuneGuess();
               fill(this.ctx, this.container, room);
            }
            case "words" -> WordPackGui.open(this.ctx, player, room, room.miniGameId());
            default -> {
            }
         }
      }
   }
}
