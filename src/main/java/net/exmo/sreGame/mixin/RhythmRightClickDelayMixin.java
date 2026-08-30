package net.exmo.sreGame.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 节奏模式使用木棍作为右键输入，移除原版 4 tick 的通用右键冷却。 */
@Mixin(Minecraft.class)
public abstract class RhythmRightClickDelayMixin {
   @Shadow
   private LocalPlayer player;

   @Shadow
   private int rightClickDelay;

   @Inject(method = "tick", at = @At("HEAD"))
   private void sre$rhythmRightClickDelay(CallbackInfo ci) {
      if (this.player != null && this.player.getMainHandItem().is(Items.STICK)) {
         this.rightClickDelay = 0;
      }
   }
}
