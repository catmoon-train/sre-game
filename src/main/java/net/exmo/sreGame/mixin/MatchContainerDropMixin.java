package net.exmo.sreGame.mixin;

import net.exmo.sreGame.util.MatchGuard;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class MatchContainerDropMixin {
   @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
   private void sre$noInventoryDrop(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
      if (!MatchGuard.lockItemDrops(player)) {
         return;
      }
      if (clickType == ClickType.THROW) {
         ci.cancel();
         return;
      }
      if (slotId == -999 && (clickType == ClickType.PICKUP || clickType == ClickType.PICKUP_ALL)) {
         ci.cancel();
      }
   }
}
