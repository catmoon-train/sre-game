package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.buildwar.BuildWarSettings;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class BuildWarSetupGui {
   private static final int[] ROUND_SLOTS = {19, 20, 21, 22, 23, 24, 25, 26};

   private BuildWarSetupGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player, GameRoom room) {
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, container, room);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color(room.isDrawWar() ? "&6⌂ &f绘画战争设置" : "&6⌂ &f建筑战争设置")
      ));
   }

   private static void fill(GameContext ctx, SimpleContainer container, GameRoom room) {
      ItemStackPane.fill(container);
      BuildWarSettings s = room.buildWarSettings();
      boolean draw = room.isDrawWar();
      container.setItem(11, GuiItems.action("clock", "&f轮数 &e" + s.rounds(), List.of("&e点击切换 1–8"), "rounds"));
      container.setItem(13, GuiItems.action("name_tag", "&f猜词时长 &e" + s.guessSeconds() + "s", List.of("&e点击切换"), "guess"));
      container.setItem(15, GuiItems.action("book", "&f主题数 &e" + s.themeCountLabel(), List.of(
         draw ? "&7主题数量，默认等于人数" : "&7主题数量，默认等于人数",
         "&e点击切换 自动 / 1–20"
      ), "themes"));
      container.setItem(16, GuiItems.action(
         s.extraBuildTogether() ? (draw ? "map" : "bricks") : "ender_eye",
         "&f多余人数 &e" + s.extraPlayersLabel(),
         List.of(
            "&7主题比人少时：其余人怎么处理",
            draw ? "&7旁观：只看不画（默认）" : "&7旁观：只看不建（默认）",
            draw ? "&7一起画：多人分到同一组" : "&7一起建：多人分到同一组",
            "&e点击切换"
         ),
         "extra"
      ));
      container.setItem(17, GuiItems.action("writable_book", "&f词库 &e" + room.wordPackLabel(), List.of(
         "&7当前 &f" + room.resolvedWords(ctx).size() + " &7词",
         "&e点击管理导入/导出"
      ), "words"));
      container.setItem(28, GuiItems.action(
         s.customTheme() ? "name_tag" : "paper",
         "&f自定主题 &e" + s.customThemeLabel(),
         List.of(
            "&7默认关：开局从词库随机或三选一",
            "&7开启：建造者聊天输入主题",
            "&7超时未输入则随机",
            "&e点击切换"
         ),
         "custom"
      ));
      container.setItem(29, GuiItems.action(
         s.pickFromThree() ? "map" : "paper",
         "&f三选一主题 &e" + s.pickFromThreeLabel(),
         List.of(
            "&7默认开：开局从 3 个主题里挑",
            "&7各组选项互不重复",
            "&7聊天输入 1/2/3 或右键物品",
            "&7自定主题开启时以自定为准",
            "&e点击切换"
         ),
         "pick3"
      ));
      for (int i = 0; i < s.rounds(); i++) {
         int round = i + 1;
         container.setItem(ROUND_SLOTS[i], GuiItems.action(
            "wooden_axe",
            (draw ? "&f第 " + round + " 轮绘画 &e" : "&f第 " + round + " 轮建造 &e") + s.buildSecondsForRound(round) + "s",
            List.of("&e点击切换本轮时长"),
            "build-round",
            "round",
            String.valueOf(round)
         ));
      }
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
         if (action == null || room == null || !room.isHost(player.getUUID())) {
            if ("back".equals(action)) {
               RoomPanelGui.open(this.ctx, player);
            }
            return;
         }
         if (SettingsArchive.handle(this.ctx, player, room, action)) {
            fill(this.ctx, this.container, room);
            return;
         }
         BuildWarSettings s = room.buildWarSettings();
         switch (action) {
            case "back" -> RoomPanelGui.open(this.ctx, player);
            case "rounds" -> {
               s.setRounds(s.rounds() >= 8 ? 1 : s.rounds() + 1);
               fill(this.ctx, this.container, room);
            }
            case "build-round" -> {
               String raw = GuiItems.extraTag(this.getSlot(slotId).getItem(), "round");
               if (raw != null) {
                  try {
                     s.cycleBuildSecondsForRound(Integer.parseInt(raw));
                  } catch (NumberFormatException ignored) {
                  }
               }
               fill(this.ctx, this.container, room);
            }
            case "guess" -> {
               s.cycleGuessSeconds();
               fill(this.ctx, this.container, room);
            }
            case "themes" -> {
               s.cycleThemeCount();
               fill(this.ctx, this.container, room);
            }
            case "extra" -> {
               s.cycleExtraPlayers();
               fill(this.ctx, this.container, room);
            }
            case "custom" -> {
               s.cycleCustomTheme();
               fill(this.ctx, this.container, room);
            }
            case "pick3" -> {
               s.cyclePickFromThree();
               fill(this.ctx, this.container, room);
            }
            case "words" -> WordPackGui.open(this.ctx, player, room, room.miniGameId());
            default -> {
            }
         }
      }
   }
}
