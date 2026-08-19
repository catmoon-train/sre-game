package net.exmo.sreGame.fraud.gui;

import java.util.List;
import java.util.UUID;
import net.exmo.sreGame.fraud.ColorCode;
import net.exmo.sreGame.fraud.FraudMasterMatch;
import net.exmo.sreGame.gui.GuiItems;
import net.exmo.sreGame.gui.ProtectedChestMenu;
import net.exmo.sreGame.util.TextUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public final class IncomingCallGui {
   private IncomingCallGui() {
   }

   public static void open(FraudMasterMatch match, ServerPlayer player) {
      if (player == null || match == null) {
         return;
      }
      UUID caller = match.phones().incomingCaller(player.getUUID());
      if (caller == null) {
         return;
      }
      SimpleContainer container = new SimpleContainer(54);
      fill(match, player, container, caller);
      player.openMenu(new SimpleMenuProvider(
         (syncId, inv, p) -> new Menu(syncId, inv, container, match, player),
         TextUtil.color("&a☎ &f来电")
      ));
   }

   private static void fill(FraudMasterMatch match, ServerPlayer viewer, SimpleContainer container, UUID caller) {
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, GuiItems.filler());
      }
      ColorCode color = match.color(caller);
      boolean switches = match.phones().incomingSwitches(viewer.getUUID());
      container.setItem(13, GuiItems.named(
         color == null ? "player_head" : color.wool(),
         match.label(caller),
         List.of(
            switches ? "&e接听将结束当前通话" : "&a正在呼叫你",
            "&7约 15 秒内未接听将自动挂断"
         )
      ));
      container.setItem(29, GuiItems.action("lime_concrete", "&a接听",
         List.of(switches ? "&7与来电方开始一对一通话" : "&7接通后才能说话"), "answer"));
      container.setItem(33, GuiItems.action("barrier", "&c拒绝",
         List.of("&7对方会收到忙音"), "reject"));
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
         this.match.handleGuiAction(player, action, GuiItems.extraTag(stack, "uuid"));
      }
   }
}
