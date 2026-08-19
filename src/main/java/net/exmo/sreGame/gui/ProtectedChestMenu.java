package net.exmo.sreGame.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public abstract class ProtectedChestMenu extends ChestMenu {
   protected final int chestSlots;
   protected final ServerPlayer viewer;

   protected ProtectedChestMenu(int syncId, Inventory playerInv, SimpleContainer container, int rows, ServerPlayer viewer) {
      super(MenuType.GENERIC_9x6, syncId, playerInv, container, rows);
      this.chestSlots = rows * 9;
      this.viewer = viewer;
   }

   @Override
   public void clicked(int slotId, int button, ClickType clickType, Player player) {
      if (player instanceof ServerPlayer sp && sp.getUUID().equals(this.viewer.getUUID())) {
         if (slotId >= 0 && slotId < this.chestSlots) {
            this.handleChestClick(sp, slotId, button, clickType);
         }
      }
   }

   protected abstract void handleChestClick(ServerPlayer player, int slotId, int button, ClickType clickType);

   @Override
   public ItemStack quickMoveStack(Player player, int index) {
      return ItemStack.EMPTY;
   }
}
