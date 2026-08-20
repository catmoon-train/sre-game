package net.exmo.sreGame.games.fraud.gui;

import net.exmo.sreGame.games.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public final class ActionGui {
   private ActionGui() {
   }

   public static void open(FraudMasterMatch match, ServerPlayer player) {
      if (player == null || match == null || match.handler() == null) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fillBackground(container);
      match.handler().fillActionGui(match, player, container);
      String title = "&6操作 &8· &f" + match.handler().type().display();
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color(title)
      ));
   }

   public static void fillBackground(SimpleContainer container) {
      ItemStack pane = GuiItems.filler();
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, pane.copy());
      }
   }

   public static void numberPad(SimpleContainer container, String current, int min, int max) {
      String shown = current == null || current.isBlank() ? "0" : current;
      container.setItem(4, GuiItems.named("paper", "&f当前 &e" + shown, java.util.List.of("&7范围 " + min + "–" + max)));
      String[] digits = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
      int[] slots = {19, 20, 21, 28, 29, 30, 37, 38, 39, 40};
      for (int i = 0; i < digits.length; i++) {
         container.setItem(slots[i], GuiItems.action("light_gray_concrete", "&f" + digits[i],
            java.util.List.of("&e点击输入"), "digit", "value", digits[i]));
      }
      container.setItem(24, GuiItems.action("barrier", "&c清除", java.util.List.of(), "clear"));
      container.setItem(33, GuiItems.action("lime_concrete", "&a提交", java.util.List.of("&7确认这个数字"), "submit"));
   }

   private static final class Menu extends ProtectedChestMenu {
      private final FraudMasterMatch match;

      Menu(int syncId, Inventory playerInv, SimpleContainer container, FraudMasterMatch match, ServerPlayer viewer) {
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
         String extra = GuiItems.extraTag(stack, "value");
         if (extra == null) {
            extra = GuiItems.extraTag(stack, "uuid");
         }
         this.match.handleGuiAction(player, action, extra);
      }
   }
}
