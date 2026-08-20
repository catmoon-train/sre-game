package net.exmo.sreGame.games.pushthebutton.gui;

import java.util.ArrayList;
import java.util.List;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.games.pushthebutton.PushTheButtonMatch;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;

public final class PtbProbeGui {
   private PtbProbeGui() {
   }

   public static void open(PushTheButtonMatch match, ServerPlayer player) {
      if (player == null || match == null) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(match, container);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color("&b探测仪")
      ));
   }

   private static void fill(PushTheButtonMatch match, SimpleContainer container) {
      for (int i = 0; i < 54; i++) {
         container.setItem(i, GuiItems.filler());
      }
      container.setItem(4, GuiItems.named("compass", "&b历史测试", List.of("&7仅显示题面与公开答案")));
      int slot = 19;
      int round = 1;
      for (PushTheButtonMatch.History rec : match.history()) {
         if (slot >= 44) {
            break;
         }
         List<String> lore = new ArrayList<>();
         lore.add("&7船长 &f" + match.ctx().name(rec.captain));
         lore.add("&7题面 &f" + rec.humanPrompt);
         for (PushTheButtonMatch.Answer answer : rec.answers) {
            lore.add("&f" + match.ctx().name(answer.player) + "&7： &e" + answer.text + " &8可疑" + answer.sus);
         }
         container.setItem(slot, GuiItems.named(rec.type.icon(), "&e第 " + round + " 轮 · " + rec.type.label(), lore));
         slot++;
         round++;
      }
      if (match.history().isEmpty()) {
         container.setItem(22, GuiItems.named("barrier", "&7还没有测试记录", List.of()));
      }
   }

   public static final class Menu extends ProtectedChestMenu {
      Menu(int syncId, Inventory playerInv, SimpleContainer container, PushTheButtonMatch match, ServerPlayer viewer) {
         super(syncId, playerInv, container, 6, viewer);
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
      }
   }
}
