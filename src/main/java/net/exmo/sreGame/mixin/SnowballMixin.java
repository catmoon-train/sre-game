package net.exmo.sreGame.mixin;

import net.exmo.sreGame.SreGame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Snowball.class)
public abstract class SnowballMixin extends ThrowableItemProjectile {
   public SnowballMixin(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
      super(entityType, level);
   }

   @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("TAIL"))
   private void sre$digSnowballCooldown(Level level, LivingEntity shooter, CallbackInfo ci) {
      if (!(shooter instanceof ServerPlayer player)) {
         return;
      }
      var ctx = SreGame.getContext();
      if (ctx != null) {
         ctx.digToDeath().onSnowballThrown(player);
      }
   }

   @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
   private void sreGame$snowballEntity(EntityHitResult result, CallbackInfo ci) {
      Snowball self = (Snowball) (Object) this;
      var ctx = SreGame.getContext();
      if (ctx == null) {
         return;
      }
      if (ctx.dodgeball().handleSnowballHit(self, result.getEntity())) {
         self.discard();
         ci.cancel();
         return;
      }
      ctx.digToDeath().handleSnowballEntity(self, result.getEntity());
   }

   @Override
   protected void onHitBlock(BlockHitResult blockHitResult) {
      var ctx = SreGame.getContext();
      if (ctx != null) {
         ctx.digToDeath().handleSnowballBlock((Snowball) (Object) this, blockHitResult.getBlockPos());
      }
      super.onHitBlock(blockHitResult);
   }


}
