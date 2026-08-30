package net.exmo.sreGame.games.partygames.api;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.exmo.sreGame.games.partygames.team.TeamPartyMatchContext;

/** One isolated state machine. It must never address players or entities outside its context. */
public interface PartyGameController {
   PartyGameDefinition definition();
   default void prepare(PartyMatchContext context) { throw new UnsupportedOperationException("This controller is not a duel game"); }
   /** Team-catalogue hook; duel controllers continue using PartyMatchContext. */
   default void prepare(TeamPartyMatchContext context) { throw new UnsupportedOperationException("This controller is not a team game"); }
   void start();
   void tick();
   boolean action(ServerPlayer player, PartyGameAction action);
   /** Return true when the current jump input must be cancelled by the host. */
   default boolean cancelJump(ServerPlayer player) { return false; }
   default boolean damage(ServerPlayer player, DamageSource source) { return true; }
   default boolean death(ServerPlayer player) { return true; }
   default boolean mobDamage(Entity entity, DamageSource source) { return false; }
   default boolean mobDeath(Entity entity, DamageSource source) { return false; }
   default boolean breakBlock(ServerPlayer player, BlockPos pos, BlockState state) { return false; }
   default void leave(UUID player) { }
   void close();
}
