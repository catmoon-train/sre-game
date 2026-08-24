package net.exmo.sreGame.games.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.exmo.sreGame.GameContext;
import net.exmo.sreGame.room.GameRoom;
import net.exmo.sreGame.room.RoomState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;

public final class DigToDeathManager {
   private final GameContext ctx;
   private final DigArenaManager arenas;
   private final Map<UUID, DigToDeathMatch> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, DigToDeathMatch> byId = new ConcurrentHashMap<>();

   public DigToDeathManager(GameContext ctx) {
      this.ctx = ctx;
      this.arenas = new DigArenaManager(ctx);
   }

   public DigArenaManager arenas() {
      return this.arenas;
   }

   public DigToDeathMatch get(UUID player) {
      return this.byPlayer.get(player);
   }

   public DigToDeathMatch getById(UUID matchId) {
      return this.byId.get(matchId);
   }

   public boolean isPlaying(ServerPlayer player) {
      return player != null && this.byPlayer.containsKey(player.getUUID());
   }

   public UUID start(GameRoom room) {
      List<UUID> seats = new ArrayList<>(room.members());
      DigArena arena = this.arenas.acquire();
      if (arena == null) {
         return null;
      }
      DigToDeathMatch match = new DigToDeathMatch(this.ctx, room, seats, arena);
      this.byId.put(match.id(), match);
      for (UUID uuid : seats) {
         this.byPlayer.put(uuid, match);
      }
      room.setActiveMatchId(match.id());
      boolean queued = this.arenas.prepare(arena, room.digToDeathSettings().layers(), () -> {
         if (this.byId.get(match.id()) == null) {
            return;
         }
         room.setState(RoomState.PLAYING);
         match.start();
      });
      if (queued) {
         this.ctx.broadcast(room, "&e正在铺设雪台，请稍候…");
      }
      return match.id();
   }

   public void tick() {
      this.arenas.tick();
      for (DigToDeathMatch match : List.copyOf(this.byId.values())) {
         match.tick();
      }
   }

   public void onLeave(ServerPlayer player) {
      DigToDeathMatch match = this.byPlayer.remove(player.getUUID());
      if (match != null) {
         match.onLeave(player.getUUID());
      }
   }

   public void remove(DigToDeathMatch match) {
      this.byId.remove(match.id());
      this.byPlayer.entrySet().removeIf(e -> e.getValue() == match);
   }

   public void endAll() {
      for (DigToDeathMatch match : List.copyOf(this.byId.values())) {
         match.endNow();
      }
   }

   public boolean tryBreak(ServerPlayer player, BlockPos pos) {
      DigToDeathMatch match = this.get(player.getUUID());
      return match != null && match.tryBreak(player, pos);
   }

   public boolean handleDeath(ServerPlayer player) {
      DigToDeathMatch match = this.get(player.getUUID());
      return match != null && match.handleDeath(player);
   }

   public boolean handleDamage(ServerPlayer player, DamageSource source) {
      DigToDeathMatch match = this.get(player.getUUID());
      return match != null && match.handleDamage(player, source);
   }

   public InteractionResult handleUseItem(ServerPlayer player, ItemStack stack) {
      DigToDeathMatch match = this.get(player.getUUID());
      return match == null ? InteractionResult.PASS : match.handleUseItem(player, stack);
   }

   public void onSnowballThrown(ServerPlayer player) {
      DigToDeathMatch match = this.get(player.getUUID());
      if (match != null) {
         match.onSnowballThrown(player);
      }
   }

   public void handleSnowballBlock(Snowball ball, BlockPos pos) {
      if (!(ball.getOwner() instanceof ServerPlayer player)) {
         return;
      }
      DigToDeathMatch match = this.get(player.getUUID());
      if (match != null) {
         match.onSnowballHitBlock(player, pos);
      }
   }

   public void handleSnowballEntity(Snowball ball, net.minecraft.world.entity.Entity hit) {
      if (!(ball.getOwner() instanceof ServerPlayer thrower) || !(hit instanceof ServerPlayer victim)) {
         return;
      }
      DigToDeathMatch match = this.get(thrower.getUUID());
      if (match != null) {
         match.onSnowballHitPlayer(thrower, victim);
      }
   }

   public boolean containsEntity(net.minecraft.world.entity.Entity entity) {
      return this.arenas.contains(entity);
   }

   public boolean openIfPlaying(ServerPlayer player) {
      DigToDeathMatch match = this.get(player.getUUID());
      if (match == null) {
         return false;
      }
      this.ctx.send(player, "&7掘一死战进行中。");
      return true;
   }
}
