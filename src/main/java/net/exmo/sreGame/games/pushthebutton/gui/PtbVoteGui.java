package net.exmo.sreGame.games.pushthebutton.gui;

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
import net.minecraft.world.item.ItemStack;

public final class PtbVoteGui {
   private PtbVoteGui() {
   }

   public static void open(PushTheButtonMatch match, ServerPlayer player) {
      if (player == null || match == null) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(match, container);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color("&6气闸投票")
      ));
   }

   private static void fill(PushTheButtonMatch match, SimpleContainer container) {
      for (int i = 0; i < 54; i++) {
         container.setItem(i, GuiItems.filler());
      }
      container.setItem(4, GuiItems.named("bell", "&f是否释放这些人？", List.of(
         "&e" + names(match),
         "&7赞成释放 → 若全是外星人则人类胜",
         "&7若混入人类则外星人胜"
      )));
      container.setItem(20, GuiItems.action("lime_concrete", "&a赞成", List.of("&7送入气闸"), "vote_yes"));
      container.setItem(24, GuiItems.action("red_concrete", "&c反对", List.of("&7取消，继续游戏"), "vote_no"));
   }

   private static String names(PushTheButtonMatch match) {
      StringBuilder sb = new StringBuilder();
      for (java.util.UUID uuid : match.nominees()) {
         if (!sb.isEmpty()) {
            sb.append("&7, ");
         }
         sb.append("&f").append(match.ctx().name(uuid));
      }
      return sb.toString();
   }

   public static final class Menu extends ProtectedChestMenu {
      private final PushTheButtonMatch match;

      Menu(int syncId, Inventory playerInv, SimpleContainer container, PushTheButtonMatch match, ServerPlayer viewer) {
         super(syncId, playerInv, container, 6, viewer);
         this.match = match;
      }

      @Override
      protected void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType) {
         ItemStack stack = this.getSlot(slotId).getItem();
         String action = GuiItems.actionTag(stack);
         if (action == null) {
            return;
         }
         this.match.handleGuiAction(player, action, null);
      }
   }
}
