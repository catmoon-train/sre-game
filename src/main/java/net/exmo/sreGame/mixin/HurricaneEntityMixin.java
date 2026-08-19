package net.exmo.sreGame.mixin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.agmas.noellesroles.content.entity.HurricaneEntity", remap = false)
public abstract class HurricaneEntityMixin {
   @Shadow
   private double height;

   @Unique
   private static final double SRE$SIZE = 1.5D;
   @Unique
   private static final double SRE$HALF = SRE$SIZE / 2.0D;
   @Unique
   private static final DustParticleOptions SRE$DUST =
      new DustParticleOptions(new Vector3f(0.72F, 0.86F, 0.95F), 0.85F);

   @Unique
   private final Map<UUID, Integer> sre$caught = new HashMap<>();

   @Inject(method = "setHeight(D)V", at = @At("HEAD"), cancellable = true)
   private void sre$lockHeight(double ignored, CallbackInfo ci) {
      this.height = SRE$SIZE;
      ci.cancel();
   }

   @Inject(method = "pullPlayers(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("HEAD"), cancellable = true)
   private void sre$smallWind(ServerLevel level, CallbackInfo ci) {
      Entity self = (Entity) (Object) this;
      Vec3 center = self.position();
      AABB box = new AABB(
         center.x - SRE$HALF, center.y, center.z - SRE$HALF,
         center.x + SRE$HALF, center.y + SRE$SIZE, center.z + SRE$HALF
      );
      for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box,
         p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
         double dx = player.getX() - self.getX();
         double dz = player.getZ() - self.getZ();
         double horiz = Math.sqrt(dx * dx + dz * dz);
         if (horiz > SRE$HALF) {
            continue;
         }
         if (horiz < 0.01D) {
            dx = 0.01D;
            dz = 0.0D;
            horiz = 0.01D;
         }
         int caughtTicks = this.sre$caught.getOrDefault(player.getUUID(), 0) + 1;
         this.sre$caught.put(player.getUUID(), caughtTicks);
         double rx = dx / horiz;
         double rz = dz / horiz;
         double tx = -rz;
         double tz = rx;
         double orbitRadius = 0.45D;
         double radialForce = (horiz - orbitRadius) * 0.4D;
         double orbitSpeed = 1.4D;
         double tangentialForce = orbitRadius * orbitSpeed * 0.15D;
         double y = Math.max(0.0D, player.getY() - self.getY());
         double heightRatio = Mth.clamp(y / SRE$SIZE, 0.0D, 1.0D);
         double upward = 0.18D - heightRatio * 0.10D + Math.sin(caughtTicks * 0.2D) * 0.04D;
         double vx = -rx * radialForce + tx * tangentialForce;
         double vz = -rz * radialForce + tz * tangentialForce;
         if (player.getY() >= self.getY() + SRE$SIZE - 0.15D || caughtTicks >= 40) {
            player.setDeltaMovement(rx * 0.85D, 0.42D, rz * 0.85D);
            this.sre$caught.remove(player.getUUID());
         } else {
            player.setDeltaMovement(vx, upward, vz);
            player.fallDistance = 0.0F;
         }
         player.hurtMarked = true;
         player.connection.send(new ClientboundSetEntityMotionPacket(player));
      }
      this.sre$caught.keySet().removeIf(uuid -> level.getPlayerByUUID(uuid) == null);
      ci.cancel();
   }

   @Inject(method = "spawnParticles(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("HEAD"), cancellable = true)
   private void sre$smallParticles(ServerLevel level, CallbackInfo ci) {
      Entity self = (Entity) (Object) this;
      int age = self.tickCount;
      for (int i = 0; i < 28; i++) {
         double h = level.random.nextDouble() * SRE$SIZE;
         double radius = Math.min(SRE$HALF, 0.15D + h * 0.35D);
         double angle = age * 0.34D + h * 1.4D + i * 0.42D;
         double x = self.getX() + Math.cos(angle) * radius;
         double z = self.getZ() + Math.sin(angle) * radius;
         double y = self.getY() + h;
         level.sendParticles(SRE$DUST, x, y, z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
      }
      ci.cancel();
   }
}
