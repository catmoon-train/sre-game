package net.exmo.sreGame.gui;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

final class ItemStackPane {
   private ItemStackPane() {
   }

   static void fill(SimpleContainer container) {
      ItemStack pane = GuiItems.filler();
      for (int i = 0; i < container.getContainerSize(); i++) {
         container.setItem(i, pane.copy());
      }
   }
}
