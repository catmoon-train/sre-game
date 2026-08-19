package net.exmo.sreGame.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "org.agmas.noellesroles.content.block_entity.scene.FogZoneBlockEntity", remap = false)
public abstract class FogZoneBlockEntityMixin {
   @Unique
   private static final double SRE$SIZE = 1.25D;
   @Unique
   private static final double SRE$HALF = SRE$SIZE / 2.0D;

   @Redirect(
      method = "serverTick",
      at = @At(
         value = "INVOKE",
         target = "Lorg/agmas/noellesroles/scene/SceneParticles;sceneRegion(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/AABB;"
      )
   )
   private static AABB sre$smallFog(BlockPos pos) {
      double cx = pos.getX() + 0.5D;
      double cy = pos.getY() + 0.5D;
      double cz = pos.getZ() + 0.5D;
      return new AABB(cx - SRE$HALF, cy - SRE$HALF, cz - SRE$HALF, cx + SRE$HALF, cy + SRE$HALF, cz + SRE$HALF);
   }
}
