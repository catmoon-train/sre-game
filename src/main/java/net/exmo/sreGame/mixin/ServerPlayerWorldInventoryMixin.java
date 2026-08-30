package net.exmo.sreGame.mixin;

import net.exmo.sreGame.player.WorldInventoryManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Swaps the active inventory before a player moves between the hub and game dimensions. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerWorldInventoryMixin {
   @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"))
   private void sre$swapWorldInventory(ServerLevel destination, double x, double y, double z, float yaw, float pitch,
                                       CallbackInfo ci) {
      WorldInventoryManager.beforeTeleport((ServerPlayer)(Object)this, destination);
   }

   @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
   private void sre$readWorldInventories(CompoundTag tag, CallbackInfo ci) {
      WorldInventoryManager.read((ServerPlayer)(Object)this, tag);
   }

   @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
   private void sre$writeWorldInventories(CompoundTag tag, CallbackInfo ci) {
      WorldInventoryManager.write((ServerPlayer)(Object)this, tag);
   }
}
