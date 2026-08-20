package net.exmo.sreGame.gui;

import java.util.List;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.games.parkour.ParkourLeaderboard;
import net.exmo.sreGame.games.parkour.ParkourSession;
import net.exmo.sreGame.games.parkour.ParkourTemplates;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class ParkourMenuGui {
   private ParkourMenuGui() {
   }

   public static void open(GameContext ctx, ServerPlayer player) {
      ParkourSession session = ctx.parkour().get(player.getUUID());
      if (session == null) {
         ctx.send(player, "&c你不在无限跑酷中。");
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(ctx, container, session, player);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, ctx, player),
         TextUtil.color("&a∞ &f无限跑酷")
      ));
   }

   private static void fill(GameContext ctx, SimpleContainer container, ParkourSession session, ServerPlayer player) {
      ItemStackPane.fill(container);
      container.setItem(11, GuiItems.action("red_wool", "&f样式 &e" + session.style().label(), List.of(
         "&7只影响方块外观",
         "&e点击切换"
      ), "style"));
      container.setItem(13, GuiItems.action("feather", "&f领先格数 &e" + session.lead(), List.of(
         "&7前方预生成的方块数量",
         "&e点击切换 1–10"
      ), "lead"));
      container.setItem(15, GuiItems.action(session.specials() ? "packed_ice" : "stone",
         "&f特殊方块 &e" + (session.specials() ? "开" : "关"), List.of(
            "&7冰 / 半砖 / 玻璃板 / 栅栏",
            session.score() > 0 ? "&c得分后不能再改" : "&e点击开关"
         ), "specials"));
      container.setItem(21, GuiItems.action(session.fallMessage() ? "note_block" : "barrier",
         "&f坠落提示 &e" + (session.fallMessage() ? "开" : "关"), List.of("&e点击开关"), "fall"));
      String diff = ParkourTemplates.difficultyLabel(session.templateDifficulty());
      container.setItem(22, GuiItems.action(session.templateDifficulty() > 0 ? "structure_block" : "gray_concrete",
         "&f跑酷模版 &e" + diff, List.of(
            "&7内置 " + ParkourTemplates.all().size() + " 套结构，随机插入赛道",
            "&7关 / 简单 / 中等 / 困难 / 极难",
            session.score() > 0 ? "&c得分后不能再改" : "&e点击切换"
         ), "templates"));
      ParkourLeaderboard.Entry best = ctx.parkour().scores().get(player.getUUID());
      container.setItem(23, GuiItems.named("gold_ingot", "&f本局 / 最高", List.of(
         "&7当前 &f" + session.score() + " &7分 · &f" + ParkourSession.formatTime(session.elapsedMs()),
         best == null ? "&7尚无最高分" : "&7最高 &e" + best.score + " &7· &f" + ParkourSession.formatTime(best.timeMs)
      )));
      var top = ctx.parkour().scores().top(5);
      java.util.ArrayList<String> lore = new java.util.ArrayList<>();
      if (top.isEmpty()) {
         lore.add("&7暂无记录");
      } else {
         int rank = 1;
         for (var entry : top) {
            lore.add("&e#" + rank + " &f" + entry.getValue().name + " &7" + entry.getValue().score
               + " · " + ParkourSession.formatTime(entry.getValue().timeMs));
            rank++;
         }
      }
      container.setItem(31, GuiItems.named("item_frame", "&e排行榜", lore));
      container.setItem(49, GuiItems.action("barrier", "&c退出跑酷", List.of(), "leave"));
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
         ParkourSession session = this.ctx.parkour().get(player.getUUID());
         if (action == null || session == null) {
            player.closeContainer();
            return;
         }
         switch (action) {
            case "style" -> session.cycleStyle();
            case "lead" -> session.cycleLead();
            case "specials" -> {
               if (session.score() > 0) {
                  this.ctx.send(player, "&c得分后不能再开关特殊方块。");
               } else {
                  session.toggleSpecials();
               }
            }
            case "fall" -> session.toggleFallMessage();
            case "templates" -> {
               if (session.score() > 0) {
                  this.ctx.send(player, "&c得分后不能再切换跑酷模版。");
               } else {
                  session.cycleTemplates();
               }
            }
            case "leave" -> {
               player.closeContainer();
               this.ctx.parkour().leave(player, true);
               return;
            }
            default -> {
               return;
            }
         }
         fill(this.ctx, this.container, session, player);
      }
   }
}
