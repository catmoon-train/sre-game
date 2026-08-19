package net.exmo.sreGame.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {
   @Invoker("removePlayer")
   void sre$removePlayer(ServerPlayer player);

   @Invoker("updatePlayer")
   void sre$updatePlayer(ServerPlayer player);
}
