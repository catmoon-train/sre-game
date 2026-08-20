package net.exmo.sreGame.games.pushthebutton.gui;

import java.util.List;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.games.pushthebutton.PromptBank;
import net.exmo.sreGame.games.pushthebutton.PtbTestType;
import net.exmo.sreGame.games.pushthebutton.PushTheButtonMatch;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public final class PtbAnswerGui {
   private PtbAnswerGui() {
   }

   public static void open(PushTheButtonMatch match, ServerPlayer player) {
      if (player == null || match == null) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(match, player, container);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color("&b答题 · " + (match.testType() == null ? "测试" : match.testType().label()))
      ));
   }

   private static void fill(PushTheButtonMatch match, ServerPlayer player, SimpleContainer container) {
      for (int i = 0; i < 54; i++) {
         container.setItem(i, GuiItems.filler());
      }
      container.setItem(4, GuiItems.named("paper", "&e" + match.promptFor(player.getUUID()), List.of("&7按你看到的提示作答")));
      if (match.testType() == PtbTestType.OPINION) {
         String[] icons = {"red_concrete", "orange_concrete", "lime_concrete", "green_concrete"};
         int[] slots = {19, 21, 23, 25};
         for (int i = 0; i < PushTheButtonMatch.OPINIONS.length; i++) {
            container.setItem(slots[i], GuiItems.action(icons[i], "&f" + PushTheButtonMatch.OPINIONS[i],
               List.of("&e点击选择"), "answer", "value", String.valueOf(i)));
         }
      } else if (match.testType() == PtbTestType.DELIB) {
         PromptBank.Delib delib = match.currentDelib();
         String a = delib == null ? "A" : delib.a();
         String b = delib == null ? "B" : delib.b();
         String c = delib == null ? "C" : delib.c();
         container.setItem(20, GuiItems.action("light_blue_concrete", "&fA. " + a, List.of("&e点击选择"), "answer", "value", "A"));
         container.setItem(22, GuiItems.action("yellow_concrete", "&fB. " + b, List.of("&e点击选择"), "answer", "value", "B"));
         container.setItem(24, GuiItems.action("pink_concrete", "&fC. " + c, List.of("&e点击选择"), "answer", "value", "C"));
      }
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
         this.match.handleGuiAction(player, action, GuiItems.extraTag(stack, "value"));
      }
   }
}
