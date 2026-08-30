package net.exmo.sreGame.games.partygames;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Runtime contract shared by the legacy party catalogue and the faithful MP2 duels. */
public interface PartySession {
   UUID id();
   PartyGameType type();
   PartyArena arena();
   void start();
   void tick();
   void onLeave(UUID uuid);
   void endNow();
   boolean handleDamage(ServerPlayer player, DamageSource source);
   boolean handleDeath(ServerPlayer player);
   boolean handleAttack(ServerPlayer player, Entity target);
   boolean handleMobDamage(Entity entity, DamageSource source);
   boolean handleMobDeath(Entity entity, DamageSource source);
   InteractionResult handleUseBlock(ServerPlayer player, BlockHitResult hit, ItemStack stack);
   InteractionResult handleUseItem(ServerPlayer player, ItemStack stack);
   InteractionResult handleUseEntity(ServerPlayer player, Entity entity);
   boolean tryBreak(ServerPlayer player, BlockPos pos, BlockState state);

   default void handleLeftClick(ServerPlayer player) { }
   /** Returns true when the jump should be cancelled after the game sees it. */
   default boolean handleJump(ServerPlayer player) { return false; }
   default void handleSneak(ServerPlayer player, boolean sneaking) { }
   default void handleHotbar(ServerPlayer player, int previousSlot, int newSlot) { }
   default void handleDrop(ServerPlayer player, ItemStack stack) { }
}
